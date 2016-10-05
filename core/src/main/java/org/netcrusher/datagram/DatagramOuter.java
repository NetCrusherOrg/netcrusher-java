package org.netcrusher.datagram;

import org.netcrusher.core.NioReactor;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.filter.ByteBufferFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
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

    private final DatagramQueue incoming;

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
        this.incoming = new DatagramQueue();
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
        // Connected DatagramChannel doesn't work with empty datagrams
        // https://bugs.openjdk.java.net/browse/JDK-8013175
        // this.channel.connect(connectAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = reactor.getSelector().register(channel, 0, this::callback);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Outer for <{}> to <{}> is started", clientAddress, connectAddress);
        }
    }

    synchronized void unfreeze() {
        if (open) {
            if (frozen) {
                int ops = incoming.isEmpty() ?
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

            if (!incoming.isEmpty()) {
                LOGGER.warn("On closing outer has {} incoming datagrams", incoming.size());
            }

            NioUtils.closeChannel(channel);

            open = false;

            LOGGER.debug("Outer for <{}> to <{}> is closed", clientAddress, connectAddress);
        }
    }

    private void closeInternal() {
        reactor.getScheduler().execute(() -> {
            inner.closeOuter(clientAddress);
            return true;
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
                LOGGER.debug("Exception in outer on read", e);
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
                LOGGER.debug("Exception in outer on write", e);
                closeInternal();
            }
        }
    }

    private void handleWritable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        DatagramQueue.Entry entry;
        int count = 0;
        while ((entry = incoming.request()) != null) {
            final boolean emptyDatagram = !entry.getBuffer().hasRemaining();
            if (emptyDatagram && count > 0) {
                // due to NIO API problem we can't differ between two cases:
                // - empty datagram is sent (send() returns 0)
                // - no free space in socket buffer (send() returns 0)
                // so we want an empty datagram to be sent first on OP_WRITE
                incoming.retry(entry);
                break;
            }

            final int sent = channel.send(entry.getBuffer(), entry.getAddress());

            if (emptyDatagram || sent > 0) {
                if (entry.getBuffer().hasRemaining()) {
                    LOGGER.warn("Datagram is split");
                    incoming.retry(entry);
                } else {
                    incoming.release(entry);
                }

                totalSentBytes.addAndGet(sent);
                totalSentDatagrams.incrementAndGet();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Send {} bytes to client <{}>", sent, entry.getAddress());
                }

                count++;
                lastOperationTimestamp = System.currentTimeMillis();
            } else {
                break;
            }
        }

        if (incoming.isEmpty()) {
            NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    private void handleReadable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        while (true) {
            final SocketAddress address = channel.receive(bb);
            if (address == null) {
                break;
            }

            if (!connectAddress.equals(address)) {
                LOGGER.trace("Datagram from <{}> will be thrown away", address);
                continue;
            }

            final int read = bb.position();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from outer for <{}>", read, clientAddress);
            }

            totalReadBytes.addAndGet(read);
            totalReadDatagrams.incrementAndGet();

            bb.flip();

            filter(bb, incomingFilters);
            inner.enqueue(clientAddress, bb);

            bb.clear();

            lastOperationTimestamp = System.currentTimeMillis();
        }
    }

    void enqueue(ByteBuffer bb) {
        filter(bb, outgoingFilters);
        boolean added = incoming.add(connectAddress, bb);
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
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

