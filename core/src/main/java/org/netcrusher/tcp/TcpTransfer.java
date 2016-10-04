package org.netcrusher.tcp;

import org.netcrusher.core.NioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

public class TcpTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpTransfer.class);

    private final String name;

    private final SelectionKey selectionKey;

    private final TcpQueue incoming;

    private final TcpQueue outgoing;

    private final AtomicLong totalRead;

    private final AtomicLong totalSent;

    private TcpTransfer other;

    TcpTransfer(String name, SelectionKey selectionKey, TcpQueue incoming, TcpQueue outgoing) throws IOException {
        this.name = name;
        this.selectionKey = selectionKey;
        this.incoming = incoming;
        this.outgoing = outgoing;
        this.totalRead = new AtomicLong();
        this.totalSent = new AtomicLong();
    }

    void freeze() {
        if (selectionKey.isValid()) {
            selectionKey.interestOps(0);
        }
    }

    void unfreeze() {
        int ops = incoming.calculateReadyBytes() == 0 ?
            SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
        selectionKey.interestOps(ops);
    }



    void handleEvent(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isReadable()) {
            handleReadable(selectionKey, outgoing);
        }
        if (selectionKey.isWritable()) {
            handleWritable(selectionKey, incoming);
        }
    }

    private void handleWritable(SelectionKey selectionKey, TcpQueue queue) throws IOException {
        final SocketChannel channel = (SocketChannel) selectionKey.channel();

        while (true) {
            final int size = queue.requestReady();
            if (size == 0) {
                NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
                break;
            }

            final long sent;
            try {
                sent = channel.write(queue.getArray(), 0, size);
            } finally {
                queue.cleanReady();
            }

            totalSent.addAndGet(sent);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Written {} bytes to {}", sent, name);
            }

            if (queue.countStage()  > 0) {
                other.notify(SelectionKey.OP_READ);
            }

            if (sent == 0) {
                break;
            }
        }
    }

    private void handleReadable(SelectionKey selectionKey, TcpQueue queue) throws IOException {
        final SocketChannel channel = (SocketChannel) selectionKey.channel();

        while (true) {
            final int size = queue.requestStage();
            if (size == 0) {
                NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_READ);
                break;
            }

            final long read;
            try {
                read = channel.read(queue.getArray(), 0, size);
            } finally {
                queue.cleanStage();
            }

            if (read < 0) {
                throw new EOFException();
            }

            totalRead.addAndGet(read);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from {}", read, name);
            }

            if (read > 0) {
                other.notify(SelectionKey.OP_WRITE);
            } else {
                break;
            }
        }
    }

    void notify(int operations) {
        if (selectionKey.isValid()) {
            NioUtils.setupInterestOps(selectionKey, operations);
        }
    }

    String getName() {
        return name;
    }

    TcpQueue getIncoming() {
        return incoming;
    }

    TcpQueue getOutgoing() {
        return outgoing;
    }

    void setOther(TcpTransfer other) {
        this.other = other;
    }

    /**
     * Request total read counter
     * @return Read bytes count
     */
    public long getTotalRead() {
        return totalRead.get();
    }

    /**
     * Request total sent counter
     * @return Sent bytes count
     */
    public long getTotalSent() {
        return totalSent.get();
    }

}
