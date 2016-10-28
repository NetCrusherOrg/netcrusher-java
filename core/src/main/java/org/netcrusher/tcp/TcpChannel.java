package org.netcrusher.tcp;

import org.netcrusher.core.meter.RateMeter;
import org.netcrusher.core.meter.RateMeterImpl;
import org.netcrusher.core.nio.NioUtils;
import org.netcrusher.core.nio.SelectionKeyControl;
import org.netcrusher.core.reactor.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

class TcpChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpChannel.class);

    private static final long LINGER_PERIOD_MS = 5000;

    private final String name;

    private final NioReactor reactor;

    private final Closeable closeable;

    private final SocketChannel channel;

    private final SelectionKeyControl selectionKeyControl;

    private final TcpQueue incoming;

    private final TcpQueue outgoing;

    private final RateMeterImpl readMeter;

    private final RateMeterImpl sentMeter;

    private TcpChannel other;

    TcpChannel(String name, NioReactor reactor, Closeable closeable,
               SocketChannel channel, TcpQueue incoming, TcpQueue outgoing) throws IOException {
        this.name = name;
        this.reactor = reactor;
        this.closeable = closeable;
        this.channel = channel;

        this.incoming = incoming;
        this.outgoing = outgoing;

        this.readMeter = new RateMeterImpl();
        this.sentMeter = new RateMeterImpl();

        SelectionKey selectionKey = reactor.getSelector().register(channel, 0, this::callback);
        this.selectionKeyControl = new SelectionKeyControl(selectionKey);
    }

    private boolean isOpen() {
        return channel.isOpen();
    }

    private void closeInternal() throws IOException {
        NioUtils.closeChannel(channel);

        closeable.close();
    }

    private void closeEOF() throws IOException {
        if (outgoing.hasReadable()) {
            NioUtils.closeChannel(channel);

            reactor.getScheduler().schedule(() -> {
                closeInternal();
                return true;
            }, LINGER_PERIOD_MS, TimeUnit.MILLISECONDS);
        } else {
            closeInternal();
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
            closeEOF();
        } catch (Exception e) {
            LOGGER.debug("Exception on {}", name, e);
            closeInternal();
        }

        // if other side is closed and there is no incoming data - close the pair
        if (this.isOpen() && !other.isOpen() && !incoming.hasReadable()) {
            closeInternal();
        }
    }

    private void handleEvent(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isWritable()) {
            handleWritableEvent();
        }

        if (selectionKey.isReadable()) {
            handleReadableEvent();
        }
    }

    private void handleWritableEvent() throws IOException {
        final TcpQueue queue = incoming;

        while (true) {
            final TcpQueueBuffers queueBuffers = queue.requestReadableBuffers();
            if (queueBuffers.isEmpty()) {
                selectionKeyControl.disableWrites();
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

        if (queue.hasWritable()) {
            other.selectionKeyControl.enableReads();
        }
    }

    private void handleReadableEvent() throws IOException {
        final TcpQueue queue = outgoing;

        while (true) {
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
            if (outgoing.hasReadable()) {
                other.handleWritableEvent();
            }
        }

        // if data still remains we raise the OP_WRITE flag
        if (outgoing.hasReadable()) {
            other.selectionKeyControl.enableWrites();
        }
    }

    TcpQueue getIncoming() {
        return incoming;
    }

    TcpQueue getOutgoing() {
        return outgoing;
    }

    void freeze() {
        if (isOpen()) {
            if (selectionKeyControl.isValid()) {
                selectionKeyControl.setNone();
            }
        } else {
            LOGGER.debug("Channel is closed on freeze");
        }
    }

    void unfreeze() {
        if (isOpen()) {
            if (incoming.hasReadable()) {
                selectionKeyControl.setAll();
            } else {
                selectionKeyControl.setReadsOnly();
            }
        } else {
            throw new IllegalStateException("Channel is closed");
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

}
