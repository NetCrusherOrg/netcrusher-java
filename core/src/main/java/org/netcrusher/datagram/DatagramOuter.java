package org.netcrusher.datagram;

import org.netcrusher.common.NioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

public class DatagramOuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramOuter.class);

    private final DatagramInner inner;

    private final InetSocketAddress clientAddress;

    private final InetSocketAddress remoteAddress;

    private final DatagramQueue incoming;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private long lastOperationTimestamp;

    private volatile boolean frozen;

    public DatagramOuter(DatagramInner inner,
                         InetSocketAddress clientAddress,
                         InetSocketAddress remoteAddress,
                         DatagramCrusherSocketOptions socketOptions) throws IOException {
        this.inner = inner;
        this.clientAddress = clientAddress;
        this.remoteAddress = remoteAddress;
        this.incoming = new DatagramQueue();
        this.lastOperationTimestamp = System.currentTimeMillis();
        this.frozen = true;

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        this.channel.configureBlocking(true);
        socketOptions.setupSocketChannel(this.channel);
        this.channel.connect(remoteAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = inner.getReactor().register(channel, 0, this::callback);

        LOGGER.debug("Outer for <{}> to <{}> is started", clientAddress, remoteAddress);
    }

    protected synchronized void unfreeze() {
        if (frozen) {
            int ops = incoming.size() > 0 ?
                SelectionKey.OP_READ | SelectionKey.OP_WRITE : SelectionKey.OP_READ;
            selectionKey.interestOps(ops);

            frozen = false;
        }
    }

    protected synchronized void freeze() {
        if (!frozen) {
            if (selectionKey.isValid()) {
                selectionKey.interestOps(0);
            }

            frozen = true;
        }
    }

    protected synchronized void close() {
        freeze();

        if (!incoming.isEmpty()) {
            LOGGER.warn("On closing outer has {} incoming datagrams with {} bytes in total",
                incoming.size(), incoming.remaining());
        }

        NioUtils.closeChannel(channel);

        LOGGER.debug("Outer for <{}> to <{}> is closed", clientAddress, remoteAddress);
    }

    private void closeInternal() {
        inner.removeOuter(clientAddress);
        close();
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        try {
            if (selectionKey.isReadable()) {
                handleReadable(selectionKey);
            }
            if (selectionKey.isWritable()) {
                handleWritable(selectionKey);
            }
        } catch (ClosedChannelException e) {
            LOGGER.debug("Outer has closed itself");
            closeInternal();
        } catch (IOException e) {
            LOGGER.error("Exception in outer", e);
            closeInternal();
        }
    }

    private void handleWritable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        DatagramQueue.Entry entry;
        while ((entry = incoming.request()) != null) {
            int written = channel.write(entry.getBuffer());
            if (written == 0) {
                incoming.retry(entry);
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Written {} bytes to outer from <{}>", written, clientAddress);
            }

            if (entry.getBuffer().hasRemaining()) {
                incoming.retry(entry);
                LOGGER.warn("Datagram is splitted");
            } else {
                incoming.release(entry);
            }

            lastOperationTimestamp = System.currentTimeMillis();
        }

        if (incoming.isEmpty()) {
            NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    private void handleReadable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        while (true) {
            int read = channel.read(bb);
            if (read < 0) {
                throw new ClosedChannelException();
            } else if (read == 0) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from outer for <{}>", read, clientAddress);
            }

            bb.flip();
            inner.enqueue(clientAddress, bb);
            bb.clear();

            lastOperationTimestamp = System.currentTimeMillis();
        }

    }

    protected void enqueue(ByteBuffer bbToCopy) {
        boolean added = incoming.add(clientAddress, bbToCopy);
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    protected long getIdleDurationMs() {
        return System.currentTimeMillis() - lastOperationTimestamp;
    }
}

