package org.netcrusher.datagram;

import org.netcrusher.common.NioReactor;
import org.netcrusher.common.NioUtils;
import org.netcrusher.filter.ByteBufferFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.List;

public class DatagramOuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramOuter.class);

    private final DatagramInner inner;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private final InetSocketAddress remoteAddress;

    private final List<ByteBufferFilter> incomingFilters;

    private final List<ByteBufferFilter> outgoingFilters;

    private final DatagramQueue pending;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private long lastOperationTimestamp;

    private boolean opened;

    private volatile boolean frozen;

    public DatagramOuter(DatagramInner inner,
                         NioReactor reactor,
                         DatagramCrusherSocketOptions socketOptions,
                         List<ByteBufferFilter> incomingFilters,
                         List<ByteBufferFilter> outgoingFilters,
                         InetSocketAddress clientAddress,
                         InetSocketAddress remoteAddress) throws IOException {
        this.inner = inner;
        this.reactor = reactor;
        this.clientAddress = clientAddress;
        this.remoteAddress = remoteAddress;
        this.pending = new DatagramQueue();
        this.lastOperationTimestamp = System.currentTimeMillis();
        this.frozen = true;
        this.opened = true;

        this.incomingFilters = incomingFilters;
        this.outgoingFilters = outgoingFilters;

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        socketOptions.setupSocketChannel(this.channel);
        this.channel.connect(remoteAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = reactor.registerSelector(channel, 0, this::callback);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Outer for <{}> to <{}> is started with {} incoming and {} outgoing filters",
                new Object[]{clientAddress, remoteAddress, incomingFilters.size(), outgoingFilters.size()});
        }
    }

    protected synchronized void unfreeze() {
        if (opened) {
            if (frozen) {
                int ops = pending.isEmpty() ?
                    SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                selectionKey.interestOps(ops);

                frozen = false;
            }
        } else {
            throw new IllegalStateException("Outer is closed");
        }
    }

    protected synchronized void freeze() {
        if (opened) {
            if (!frozen) {
                if (selectionKey.isValid()) {
                    selectionKey.interestOps(0);
                }

                frozen = true;
            }
        } else {
            throw new IllegalStateException("Outer is closed");
        }
    }

    protected synchronized void close() {
        if (opened) {
            freeze();

            if (!pending.isEmpty()) {
                LOGGER.warn("On closing outer has {} incoming datagrams with {} bytes in total",
                    pending.size(), pending.bytes());
            }

            NioUtils.closeChannel(channel);

            opened = false;

            LOGGER.debug("Outer for <{}> to <{}> is closed", clientAddress, remoteAddress);
        }
    }

    protected void closeInternal() {
        reactor.execute(() -> {
            inner.closeOuter(clientAddress);
            return null;
        });
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isReadable()) {
            try {
                handleReadable(selectionKey);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on read");
                closeInternal();
            } catch (EOFException e) {
                LOGGER.debug("EOF on read");
                closeInternal();
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port in unreachable on read");
                closeInternal();
            } catch (IOException e) {
                LOGGER.error("Exception in outer on read", e);
                closeInternal();
            }
        }

        if (selectionKey.isWritable()) {
            try {
                handleWritable(selectionKey);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on write");
                closeInternal();
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port in unreachable on write");
                closeInternal();
            } catch (IOException e) {
                LOGGER.error("Exception in outer on write", e);
                closeInternal();
            }
        }
    }

    private void handleWritable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        DatagramQueue.Entry entry;
        while ((entry = pending.request()) != null) {
            int written = channel.write(entry.getBuffer());
            if (written == 0) {
                pending.retry(entry);
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Written {} bytes to outer from <{}>", written, clientAddress);
            }

            if (entry.getBuffer().hasRemaining()) {
                pending.retry(entry);
                LOGGER.warn("Datagram is splitted");
            } else {
                pending.release(entry);
            }

            lastOperationTimestamp = System.currentTimeMillis();
        }

        if (pending.isEmpty()) {
            NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    private void handleReadable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        while (true) {
            int read = channel.read(bb);
            if (read < 0) {
                throw new EOFException();
            }
            if (read == 0) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from outer for <{}>", read, clientAddress);
            }

            bb.flip();
            inner.enqueue(clientAddress, filterIncoming(bb));
            bb.clear();

            lastOperationTimestamp = System.currentTimeMillis();
        }
    }

    protected void enqueue(ByteBuffer bbToCopy) {
        boolean added = pending.add(clientAddress, filterOutgoing(bbToCopy));
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    private ByteBuffer filterIncoming(ByteBuffer bb) {
        ByteBuffer filtered = bb;

        if (!incomingFilters.isEmpty()) {
            for (ByteBufferFilter filter : incomingFilters) {
                filtered = filter.filter(filtered);
            }
        }

        return filtered;
    }

    private ByteBuffer filterOutgoing(ByteBuffer bb) {
        ByteBuffer filtered = bb;

        if (!outgoingFilters.isEmpty()) {
            for (ByteBufferFilter filter : outgoingFilters) {
                filtered = filter.filter(filtered);
            }
        }

        return filtered;
    }

    protected long getIdleDurationMs() {
        return System.currentTimeMillis() - lastOperationTimestamp;
    }
}

