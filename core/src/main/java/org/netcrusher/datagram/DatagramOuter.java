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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DatagramOuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramOuter.class);

    private final DatagramInner inner;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private final InetSocketAddress connectAddress;

    private final ByteBufferFilter[] incomingFilters;

    private final ByteBufferFilter[] outgoingFilters;

    private final DatagramQueue pending;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private final AtomicLong totalSentBytes;

    private final AtomicLong totalReadBytes;

    private final AtomicInteger totalSentDatagrams;

    private final AtomicInteger totalReadDatagrams;

    private boolean open;

    private volatile boolean frozen;

    private long lastOperationTimestamp;

    DatagramOuter(
            DatagramInner inner,
            NioReactor reactor,
            DatagramCrusherSocketOptions socketOptions,
            ByteBufferFilter[] incomingFilters,
            ByteBufferFilter[] outgoingFilters,
            InetSocketAddress clientAddress,
            InetSocketAddress connectAddress) throws IOException
    {
        this.inner = inner;
        this.reactor = reactor;
        this.clientAddress = clientAddress;
        this.connectAddress = connectAddress;
        this.pending = new DatagramQueue();
        this.lastOperationTimestamp = System.currentTimeMillis();
        this.frozen = true;
        this.open = true;

        this.totalReadBytes = new AtomicLong(0);
        this.totalSentBytes = new AtomicLong(0);
        this.totalReadDatagrams = new AtomicInteger(0);
        this.totalSentDatagrams = new AtomicInteger(0);

        this.incomingFilters = incomingFilters;
        this.outgoingFilters = outgoingFilters;

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        socketOptions.setupSocketChannel(this.channel);
        this.channel.connect(connectAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = reactor.registerSelector(channel, 0, this::callback);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Outer for <{}> to <{}> is started", clientAddress, connectAddress);
        }
    }

    synchronized void unfreeze() {
        if (open) {
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

    synchronized void freeze() {
        if (open) {
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

    synchronized void closeExternal() {
        if (open) {
            freeze();

            if (pending.bytes() > 0) {
                LOGGER.warn("On closing outer has {} incoming datagrams with {} bytes in total",
                    pending.size(), pending.bytes());
            }

            NioUtils.closeChannel(channel);

            open = false;

            LOGGER.debug("Outer for <{}> to <{}> is closed", clientAddress, connectAddress);
        }
    }

    private void closeInternal() {
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
            int sent = channel.write(entry.getBuffer());
            if (sent == 0) {
                pending.retry(entry);
                break;
            }

            totalSentBytes.addAndGet(sent);
            totalSentDatagrams.incrementAndGet();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Written {} bytes to outer from <{}>", sent, clientAddress);
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

            totalReadBytes.addAndGet(read);
            totalReadDatagrams.incrementAndGet();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from outer for <{}>", read, clientAddress);
            }

            bb.flip();

            filter(bb, incomingFilters);

            if (bb.hasRemaining()) {
                inner.enqueue(clientAddress, bb);
            }

            bb.clear();

            lastOperationTimestamp = System.currentTimeMillis();
        }
    }

    void enqueue(ByteBuffer bb) {
        filter(bb, outgoingFilters);
        if (bb.hasRemaining()) {
            boolean added = pending.add(bb);
            if (added) {
                NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
            }
        }
    }

    private void filter(ByteBuffer bb, ByteBufferFilter[] filters) {
        if (filters != null) {
            for (ByteBufferFilter filter : filters) {
                filter.filter(bb);
            }
        }
    }

    /**
     * Get inner client address for this connection
     * @return Address
     */
    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    /**
     * How long was the connection idle
     * @return Idle duration is milliseconds
     */
    public long getIdleDurationMs() {
        return System.currentTimeMillis() - lastOperationTimestamp;
    }

    /**
     * How many bytes was sent from outer
     * @return Bytes
     */
    public long getTotalSentBytes() {
        return totalSentBytes.get();
    }

    /**
     * How many bytes was received by outer
     * @return Bytes
     */
    public long getTotalReadBytes() {
        return totalReadBytes.get();
    }

    /**
     * How many datagrams was sent by outer
     * @return Datagram count
     */
    public int getTotalSentDatagrams() {
        return totalSentDatagrams.get();
    }

    /**
     * How many datagrams was received by outer
     * @return Datagram count
     */
    public int getTotalReadDatagrams() {
        return totalReadDatagrams.get();
    }
}

