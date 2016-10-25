package org.netcrusher.tcp;

import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.meter.RateMeter;
import org.netcrusher.core.meter.RateMeterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

class TcpTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpTransfer.class);

    private static final long LINGER_PERIOD_MS = 5000;

    private final String name;

    private final NioReactor reactor;

    private final Closeable closeable;

    private final SocketChannel channel;

    private final SelectionKey selectionKey;

    private final TcpQueue incoming;

    private final TcpQueue outgoing;

    private final RateMeterImpl readMeter;

    private final RateMeterImpl sentMeter;

    private TcpTransfer other;

    TcpTransfer(String name, NioReactor reactor, Closeable closeable,
                SocketChannel channel, TcpQueue incoming, TcpQueue outgoing) throws IOException {
        this.name = name;
        this.reactor = reactor;
        this.closeable = closeable;
        this.channel = channel;
        this.selectionKey = reactor.getSelector().register(channel, 0, this::callback);
        this.incoming = incoming;
        this.outgoing = outgoing;
        this.readMeter = new RateMeterImpl();
        this.sentMeter = new RateMeterImpl();
    }

    private boolean isOpen() {
        return channel.isOpen();
    }

    private void closeInternal() throws IOException {
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
        } catch (EOFException | ClosedChannelException e) {
            LOGGER.debug("EOF on transfer or channel is closed on {}", name);
            closeEOF();
        } catch (IOException e) {
            LOGGER.debug("IO exception on {}", name, e);
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
                NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
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
            other.enableOperations(SelectionKey.OP_READ);
        }
    }

    private void handleReadableEvent() throws IOException {
        final TcpQueue queue = outgoing;

        while (true) {
            final TcpQueueBuffers queueBuffers = queue.requestWritableBuffers();
            if (queueBuffers.isEmpty()) {
                NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_READ);
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
            other.enableOperations(SelectionKey.OP_WRITE);
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
            if (selectionKey.isValid()) {
                selectionKey.interestOps(0);
            }
        } else {
            LOGGER.debug("Channel is closed on freeze");
        }
    }

    void unfreeze() {
        if (isOpen()) {
            int ops = incoming.hasReadable() ?
                SelectionKey.OP_READ | SelectionKey.OP_WRITE : SelectionKey.OP_READ;
            selectionKey.interestOps(ops);
        } else {
            throw new IllegalStateException("Channel is closed");
        }
    }

    void setOther(TcpTransfer other) {
        this.other = other;
    }

    private void enableOperations(int operations) {
        if (selectionKey.isValid()) {
            NioUtils.setupInterestOps(selectionKey, operations);
        }
    }

    /**
     * Request total read counter for this transfer
     * @return Read bytes count
     */
    RateMeter getReadMeter() {
        return readMeter;
    }

    /**
     * Request total sent counter for this transfer
     * @return Sent bytes count
     */
    RateMeter getSentMeter() {
        return sentMeter;
    }

}
