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
import java.util.concurrent.TimeUnit;

class TcpChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpChannel.class);

    private static final long LINGER_PERIOD_NS = TimeUnit.SECONDS.toNanos(10);

    private final String name;

    private final NioReactor reactor;

    private final Runnable ownerClose;

    private final SocketChannel channel;

    private final SelectionKeyControl selectionKeyControl;

    private final TcpQueue incomingQueue;

    private final TcpQueue outgoingQueue;

    private final RateMeterImpl readMeter;

    private final RateMeterImpl sentMeter;

    private final State state;

    private TcpChannel other;

    TcpChannel(String name, NioReactor reactor, Runnable ownerClose, SocketChannel channel,
               TcpQueue incomingQueue, TcpQueue outgoingQueue) throws IOException
    {
        this.name = name;
        this.reactor = reactor;
        this.ownerClose = ownerClose;
        this.channel = channel;

        this.incomingQueue = incomingQueue;
        this.outgoingQueue = outgoingQueue;

        this.readMeter = new RateMeterImpl();
        this.sentMeter = new RateMeterImpl();

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

                if (sentMeter.getTotalCount() > 0) {
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

        if (other.state.isReadEof() && !incomingQueue.hasReadable()) {
            this.close();
        }

        if (this.state.is(State.CLOSED)) {
            if (other.state.is(State.CLOSED) || !outgoingQueue.hasReadable()) {
                closeAll();
            } else {
                closeAllDeferred();
            }
        } else {
            closeAllDeferred();
        }
    }

    private void closeLocal() {
        this.close();

        if (other.state.is(State.OPEN) && outgoingQueue.hasReadable()) {
            closeAllDeferred();
        } else {
            closeAll();
        }
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        try {
            handleEvent(selectionKey);
        } catch (EOFException e) {
            LOGGER.debug("EOF on transfer on {}", name);
            closeEOF();
        } catch (ClosedChannelException e) {
            LOGGER.debug("Channel closed on {}", name);
            closeLocal();
        } catch (Exception e) {
            LOGGER.debug("Exception on {}", name, e);
            closeAll();
        }

        // if other side is closed and there is no incoming data - close the pair
        if (this.state.is(State.OPEN) && other.state.not(State.OPEN) && !incomingQueue.hasReadable()) {
            closeAll();
        }
    }

    private void handleEvent(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isWritable()) {
            handleWritableEvent(false);
        }

        if (selectionKey.isReadable()) {
            handleReadableEvent();
        }
    }

    private void handleWritableEvent(boolean forced) throws IOException {
        final TcpQueue queue = incomingQueue;

        while (channel.isOpen() && state.isWritable()) {
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
                LOGGER.trace("Written {} bytes to {}", sent, name);
            }

            sentMeter.update(sent);
        }

        if (queue.hasWritable() && other.state.isReadable()) {
            other.selectionKeyControl.enableReads();
        }
    }

    private void handleReadableEvent() throws IOException {
        final TcpQueue queue = outgoingQueue;

        while (channel.isOpen() && state.isReadable()) {
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
                selectionKeyControl.disableReads();
                throw new EOFException();
            }

            if (read == 0) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from {}", read, name);
            }

            readMeter.update(read);

            // try to immediately sent all the pending data to the paired socket
            if (outgoingQueue.hasReadable() && other.state.isWritable()) {
                other.handleWritableEvent(true);
            }
        }

        // if data still remains we raise the OP_WRITE flag
        if (outgoingQueue.hasReadable() && other.state.isWritable()) {
            other.selectionKeyControl.enableWrites();
        }
    }

    void freeze() {
        if (state.is(State.OPEN)) {
            if (selectionKeyControl.isValid()) {
                selectionKeyControl.setNone();
            }

            state.set(State.FROZEN);
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
        }
    }

    private void throttleSend(long delayNs) {
        if (!this.state.isSendThrottled()) {
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
        if (this.state.isSendThrottled()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Channel {} is unthrottled", name);
            }

            this.state.setSendThrottled(false);

            if (this.selectionKeyControl.isValid() && state.is(State.OPEN)) {
                this.selectionKeyControl.enableWrites();
            }
        }
    }

    void setOther(TcpChannel other) {
        this.other = other;
    }

    RateMeter getReadMeter() {
        return readMeter;
    }

    RateMeter getSentMeter() {
        return sentMeter;
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

        public void setReadEof(boolean readEof) {
            this.readEof = readEof;
        }

        private boolean isReadEof() {
            return is(CLOSED) || this.readEof;
        }

        public boolean isSendThrottled() {
            return sendThrottled;
        }

        public void setSendThrottled(boolean sendThrottled) {
            this.sendThrottled = sendThrottled;
        }

        private boolean isWritable() {
            return is(OPEN) && !sendThrottled;
        }

        private boolean isReadable() {
            return is(OPEN) && !readEof;
        }
    }

}
