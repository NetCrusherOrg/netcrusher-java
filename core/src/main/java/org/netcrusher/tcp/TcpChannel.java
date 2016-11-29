package org.netcrusher.tcp;

import org.netcrusher.core.meter.RateMeter;
import org.netcrusher.core.meter.RateMeterImpl;
import org.netcrusher.core.nio.NioUtils;
import org.netcrusher.core.nio.SelectionKeyControl;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

class TcpChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpChannel.class);

    private static final long LINGER_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(500);

    private final String name;

    private final NioReactor reactor;

    private final Runnable ownerClose;

    private final SocketChannel channel;

    private final SelectionKeyControl selectionKeyControl;

    private final TcpQueue incomingQueue;

    private final TcpQueue outgoingQueue;

    private final Meters meters;

    private final State state;

    private final Queue<Runnable> postOperations;

    private TcpChannel other;

    TcpChannel(String name, NioReactor reactor, Runnable ownerClose, SocketChannel channel,
               TcpQueue incomingQueue, TcpQueue outgoingQueue) throws IOException
    {
        this.name = name;
        this.reactor = reactor;
        this.ownerClose = ownerClose;
        this.channel = channel;

        this.postOperations = new LinkedList<>();

        this.incomingQueue = incomingQueue;
        this.outgoingQueue = outgoingQueue;

        this.meters = new Meters();

        SelectionKey selectionKey = reactor.getSelector().register(channel, 0, this::callback);
        this.selectionKeyControl = new SelectionKeyControl(selectionKey);

        this.state = new State(State.FROZEN);
    }

    void close() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                if (meters.sentBytes.getTotalCount() > 0) {
                    NioUtils.close(channel);
                } else {
                    NioUtils.closeNoLinger(channel);
                }

                state.set(State.CLOSED);

                if (LOGGER.isDebugEnabled()) {
                    long incomingBytes = incomingQueue.calculateReadableBytes();
                    if (incomingBytes > 0) {
                        LOGGER.debug("Channel {} has {} incoming bytes when closing", name, incomingBytes);
                    }
                }

                return true;
            } else {
                return false;
            }
        });
    }

    private void closeAll()  {
        this.close();
        ownerClose.run();
    }

    private void closeAllDeferred() {
        reactor.getSelector().schedule(this::closeAll, LINGER_PERIOD_NS);
    }

    private void closeEOF() {
        this.state.setReadEof(true);

        other.postOperations.add(() -> other.shutdownWrite());
        other.processPostOperations();

        if (other.state.isReadEof() && !incomingQueue.hasReadable() && !outgoingQueue.hasReadable()) {
            closeAllDeferred();
        }
    }

    private void closeLocal() {
        this.close();

        other.postOperations.add(() -> other.closeAll());
        other.processPostOperations();
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        try {
            if (selectionKey.isWritable()) {
                handleWritableEvent(false);
            }
        } catch (ClosedChannelException e) {
            LOGGER.debug("Channel closed on write {}", name);
            closeLocal();
        } catch (Exception e) {
            LOGGER.error("Exception on {}", name, e);
            closeAll();
        }

        try {
            if (selectionKey.isReadable()) {
                handleReadableEvent();
            }
        } catch (EOFException e) {
            LOGGER.debug("EOF on transfer on {}", name);
            closeEOF();
        } catch (ClosedChannelException e) {
            LOGGER.debug("Channel closed on {}", name);
            closeLocal();
        } catch (Exception e) {
            LOGGER.error("Exception on {}", name, e);
            closeAll();
        }
    }

    private void handleWritableEvent(boolean forced) throws IOException {
        final TcpQueue queue = incomingQueue;

        while (state.isWritable()) {
            final TcpQueueBuffers queueBuffers = queue.requestReadableBuffers();
            if (queueBuffers.isEmpty()) {
                if (queueBuffers.getDelayNs() > 0) {
                    throttleSend(queueBuffers.getDelayNs());
                } else {
                    selectionKeyControl.disableWrites();
                }
                break;
            }

            final long sent;
            try {
                sent = channel.write(queueBuffers.getArray(), queueBuffers.getOffset(), queueBuffers.getCount());
            } finally {
                queue.releaseReadableBuffers();
            }

            if (sent == 0) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Written {} bytes to {} (forced={})", new Object[] { sent, name, forced });
            }

            meters.sentBytes.update(sent);
        }

        other.suggestDeferredRead();

        processPostOperations();
    }

    private void handleReadableEvent() throws IOException {
        final TcpQueue queue = outgoingQueue;

        while (state.isReadable()) {
            final TcpQueueBuffers queueBuffers = queue.requestWritableBuffers();
            if (queueBuffers.isEmpty()) {
                selectionKeyControl.disableReads();
                break;
            }

            final long read;
            try {
                read = channel.read(queueBuffers.getArray(), queueBuffers.getOffset(), queueBuffers.getCount());
            } finally {
                queue.releaseWritableBuffers();
            }

            if (read < 0) {
                if (channel.isOpen()) {
                    selectionKeyControl.disableReads();
                    throw new EOFException();
                } else {
                    throw new ClosedChannelException();
                }
            }

            if (read == 0) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from {}", read, name);
            }

            meters.readBytes.update(read);

            other.suggestImmediateSent();
        }

        other.suggestDeferredSent();
    }

    private void processPostOperations() {
        if (!incomingQueue.hasReadable() && other.state.isReadEof()) {
            while (!postOperations.isEmpty()) {
                Runnable operation = postOperations.poll();
                operation.run();
            }
        }
    }

    private void shutdownWrite() {
        if (channel.isOpen()) {
            try {
                channel.shutdownOutput();
            } catch (IOException e) {
                LOGGER.error("Fail to shutdown output", e);
            }
        }
    }

    private void suggestDeferredRead() {
        if (outgoingQueue.hasWritable() && state.isReadable()) {
            selectionKeyControl.enableReads();
        }
    }

    private void suggestDeferredSent() {
        if (incomingQueue.hasReadable() && state.isWritable()) {
            selectionKeyControl.enableWrites();
        }
    }

    private void suggestImmediateSent() throws IOException {
        if (incomingQueue.hasReadable() && state.isWritable()) {
            handleWritableEvent(true);
        }
    }

    void freeze() {
        if (state.is(State.OPEN)) {
            if (selectionKeyControl.isValid()) {
                selectionKeyControl.setNone();
            }

            state.set(State.FROZEN);
        } else {
            LOGGER.warn("Freezing while not open");
        }
    }

    void unfreeze() {
        if (state.is(State.FROZEN)) {
            if (incomingQueue.hasReadable()) {
                selectionKeyControl.setAll();
            } else {
                selectionKeyControl.setReadsOnly();
            }

            state.set(State.OPEN);
        } else {
            LOGGER.warn("Unfreezing while not frozen");
        }
    }

    boolean isFrozen() {
        return state.isAnyOf(State.CLOSED | State.FROZEN);
    }

    private void throttleSend(long delayNs) {
        if (this.state.is(State.OPEN) && !this.state.isSendThrottled()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Channel {} is throttled on {}ns", name, delayNs);
            }

            this.state.setSendThrottled(true);

            if (this.selectionKeyControl.isValid()) {
                this.selectionKeyControl.disableWrites();
            }

            reactor.getSelector().schedule(this::unthrottleSend, delayNs);
        }
    }

    private void unthrottleSend() {
        if (this.state.is(State.OPEN) && this.state.isSendThrottled()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Channel {} is unthrottled", name);
            }

            this.state.setSendThrottled(false);

            if (this.selectionKeyControl.isValid()) {
                this.selectionKeyControl.enableWrites();
            }
        }
    }

    void setOther(TcpChannel other) {
        this.other = other;
    }

    RateMeter getReadBytesMeter() {
        return meters.readBytes;
    }

    RateMeter getSentBytesMeter() {
        return meters.sentBytes;
    }

    private static final class State extends BitState {

        private static final int OPEN = bit(0);

        private static final int FROZEN = bit(1);

        private static final int CLOSED = bit(2);

        private boolean readEof;

        private boolean sendThrottled;

        private State(int state) {
            super(state);
            this.readEof = false;
            this.sendThrottled = false;
        }

        private void setReadEof(boolean readEof) {
            this.readEof = readEof;
        }

        private boolean isReadEof() {
            return is(CLOSED) || this.readEof;
        }

        private boolean isSendThrottled() {
            return sendThrottled;
        }

        private void setSendThrottled(boolean sendThrottled) {
            this.sendThrottled = sendThrottled;
        }

        private boolean isWritable() {
            return is(OPEN) && !sendThrottled;
        }

        private boolean isReadable() {
            return is(OPEN) && !readEof;
        }
    }

    private static final class Meters {

        private final RateMeterImpl readBytes;

        private final RateMeterImpl sentBytes;

        private Meters() {
            this.readBytes = new RateMeterImpl();
            this.sentBytes = new RateMeterImpl();
        }
    }

}
