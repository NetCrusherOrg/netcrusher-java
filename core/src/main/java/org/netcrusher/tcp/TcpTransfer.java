package org.netcrusher.tcp;

import org.netcrusher.common.NioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

public class TcpTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpTransfer.class);

    private final String name;

    private final SelectionKey otherSideKey;

    private final TcpTransferQueue incoming;

    private final TcpTransferQueue outgoing;

    public TcpTransfer(String name, SelectionKey otherSideKey, TcpTransferQueue incoming, TcpTransferQueue outgoing) {
        this.name = name;
        this.otherSideKey = otherSideKey;
        this.incoming = incoming;
        this.outgoing = outgoing;
    }

    public String getName() {
        return name;
    }

    protected void handleEvent(SelectionKey selectionKey) throws IOException {
        if (!selectionKey.isValid()) {
            throw new IOException("Selection key is not valid");
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Started event handling for {} [incoming={}, outgoing={}]",
                new Object[] { name, incoming.pending(), outgoing.pending() });
        }

        if (selectionKey.isReadable()) {
            handleReadable(selectionKey, outgoing);
        }

        if (selectionKey.isWritable()) {
            handleWritable(selectionKey, incoming);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Finished event handling for {} [incoming={}, outgoing={}]",
                new Object[] { name, incoming.pending(), outgoing.pending() });
        }
    }

    private void handleWritable(SelectionKey selectionKey, TcpTransferQueue queue) throws IOException {
        while (true) {
            ByteBuffer bb = queue.requestTailBuffer();
            if (bb == null) {
                NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
                break;
            }

            int written = write(selectionKey.channel(), bb);
            LOGGER.trace("Written {} bytes to {}", written, name);
            queue.changePending(-written);

            if (!bb.hasRemaining()) {
                queue.releaseTailBuffer();
                informOtherSide(SelectionKey.OP_READ);
            } else {
                break;
            }
        }
    }

    private void handleReadable(SelectionKey selectionKey, TcpTransferQueue queue) throws IOException {
        while (true) {
            ByteBuffer bb = queue.requestHeadBuffer();
            if (bb == null) {
                NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_READ);
                break;
            }

            int read = read(selectionKey.channel(), bb);
            if (read < 0) {
                throw new EOFException();
            }

            LOGGER.trace("Read {} bytes from {}", read, name);
            queue.changePending(+read);

            if (read > 0) {
                informOtherSide(SelectionKey.OP_WRITE);
            }

            if (!bb.hasRemaining()) {
                queue.releaseHeadBuffer();
            } else {
                break;
            }
        }
    }

    private int write(SelectableChannel selectableChannel, ByteBuffer bb) throws IOException {
        final WritableByteChannel channel = (WritableByteChannel) selectableChannel;

        return channel.write(bb);
    }

    private int read(SelectableChannel selectableChannel, ByteBuffer bb) throws IOException {
        final ReadableByteChannel channel = (ReadableByteChannel) selectableChannel;

        return channel.read(bb);
    }

    protected void informOtherSide(int operations) {
        if (otherSideKey.isValid()) {
            NioUtils.setupInterestOps(otherSideKey, operations);
        }
    }

    protected TcpTransferQueue getIncoming() {
        return incoming;
    }

    protected TcpTransferQueue getOutgoing() {
        return outgoing;
    }

    @Override
    public String toString() {
        return "TcpTransfer{" + name + '}';
    }
}
