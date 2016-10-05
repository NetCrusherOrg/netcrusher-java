package org.netcrusher.datagram;

import org.netcrusher.core.NioReactor;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.filter.ByteBufferFilter;
import org.netcrusher.core.filter.ByteBufferFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DatagramInner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramInner.class);

    private final DatagramCrusher crusher;

    private final NioReactor reactor;

    private final DatagramCrusherSocketOptions socketOptions;

    private final ByteBufferFilterRepository filters;

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final long maxIdleDurationMs;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private final Map<InetSocketAddress, DatagramOuter> outers;

    private final DatagramQueue incoming;

    private final AtomicLong totalSentBytes;

    private final AtomicLong totalReadBytes;

    private final AtomicInteger totalSentDatagrams;

    private final AtomicInteger totalReadDatagrams;

    private boolean open;

    private volatile boolean frozen;

    DatagramInner(
            DatagramCrusher crusher,
            NioReactor reactor,
            DatagramCrusherSocketOptions socketOptions,
            ByteBufferFilterRepository filters,
            InetSocketAddress bindAddress,
            InetSocketAddress connectAddress,
            long maxIdleDurationMs) throws IOException
    {
        this.crusher = crusher;
        this.reactor = reactor;
        this.filters = filters;
        this.socketOptions = socketOptions;
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.outers = new ConcurrentHashMap<>(32);
        this.incoming = new DatagramQueue();
        this.maxIdleDurationMs = maxIdleDurationMs;
        this.frozen = true;
        this.open = true;

        this.totalReadBytes = new AtomicLong(0);
        this.totalSentBytes = new AtomicLong(0);
        this.totalReadDatagrams = new AtomicInteger(0);
        this.totalSentDatagrams = new AtomicInteger(0);

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        socketOptions.setupSocketChannel(this.channel);
        this.channel.bind(bindAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = reactor.getSelector().register(channel, 0, this::callback);

        LOGGER.debug("Inner on <{}> is started", bindAddress);
    }

    synchronized void unfreeze() throws IOException {
        if (open) {
            if (frozen) {
                reactor.getSelector().executeOp(() -> {
                    int ops = incoming.isEmpty() ?
                        SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    selectionKey.interestOps(ops);

                    outers.values().forEach(DatagramOuter::unfreeze);

                    return true;
                });

                frozen = false;
            }
        } else {
            throw new IllegalStateException("Inner is closed");
        }
    }

    synchronized void freeze() throws IOException {
        if (open) {
            if (!frozen) {
                reactor.getSelector().executeOp(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(0);
                    }

                    outers.values().forEach(DatagramOuter::freeze);

                    return true;
                });

                frozen = true;
            }
        } else {
            LOGGER.debug("Component is closed on freeze");
        }
    }

    synchronized boolean isFrozen() {
        if (open) {
            return frozen;
        } else {
            throw new IllegalStateException("Inner is closed");
        }
    }

    synchronized void closeExternal() throws IOException {
        if (open) {
            freeze();

            if (!incoming.isEmpty()) {
                LOGGER.warn("On closing inner has {} incoming datagrams", incoming.size());
            }

            outers.values().forEach(DatagramOuter::closeExternal);
            outers.clear();

            NioUtils.closeChannel(channel);

            reactor.getSelector().wakeup();

            open = false;

            LOGGER.debug("Inner on <{}> is closed", bindAddress);
        }
    }

    void closeOuter(InetSocketAddress clientAddress) {
        DatagramOuter outer = outers.remove(clientAddress);
        if (outer != null) {
            outer.closeExternal();
        }
    }

    private void closeInternal() {
        reactor.getScheduler().execute(() -> {
            crusher.close();
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
            } catch (IOException e) {
                LOGGER.debug("Exception in inner on read", e);
                closeInternal();
            }
        }

        if (selectionKey.isWritable()) {
            try {
                handleWritable(selectionKey);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on write");
                closeInternal();
            } catch (IOException e) {
                LOGGER.debug("Exception in inner on write", e);
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
            final InetSocketAddress address = (InetSocketAddress) channel.receive(bb);
            if (address == null) {
                break;
            }

            final int read = bb.position();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received {} bytes from inner <{}>", read, address);
            }

            totalReadBytes.addAndGet(read);
            totalReadDatagrams.incrementAndGet();

            DatagramOuter outer = requestOuter(address);

            bb.flip();

            outer.enqueue(bb);

            bb.clear();
        }
    }

    private DatagramOuter requestOuter(InetSocketAddress address) throws IOException {
        DatagramOuter outer = outers.get(address);

        if (outer == null) {
            if (maxIdleDurationMs > 0) {
                clearOuters(maxIdleDurationMs);
            }

            ByteBufferFilter[] incomingFilters = filters.getIncoming().createFilters(address);
            ByteBufferFilter[] outgoingFilters = filters.getOutgoing().createFilters(address);

            outer = new DatagramOuter(this, reactor, socketOptions,
                incomingFilters, outgoingFilters, address, connectAddress);
            outer.unfreeze();

            outers.put(address, outer);
        }

        return outer;
    }

    private void clearOuters(long maxIdleDurationMs) {
        int countBefore = outers.size();
        if (countBefore > 0) {
            Iterator<DatagramOuter> outerIterator = outers.values().iterator();

            while (outerIterator.hasNext()) {
                DatagramOuter outer = outerIterator.next();

                if (outer.getIdleDurationMs() > maxIdleDurationMs) {
                    outer.closeExternal();
                    outerIterator.remove();
                }
            }

            int countAfter = outers.size();
            if (countAfter < countBefore) {
                LOGGER.debug("Outer connections are cleared ({} -> {})", countBefore, countAfter);
            }
        }
    }

    void enqueue(InetSocketAddress address, ByteBuffer bbToCopy) {
        boolean added = incoming.add(address, bbToCopy);
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    Collection<DatagramOuter> getOuters() {
        return new ArrayList<>(outers.values());
    }

    /**
     * How many bytes was sent from inner
     * @return Bytes
     */
    public long getTotalSentBytes() {
        return totalSentBytes.get();
    }

    /**
     * How many bytes was received by inner
     * @return Bytes
     */
    public long getTotalReadBytes() {
        return totalReadBytes.get();
    }

    /**
     * How many datagrams was sent by inner
     * @return Datagram count
     */
    public int getTotalSentDatagrams() {
        return totalSentDatagrams.get();
    }

    /**
     * How many datagrams was received by inner
     * @return Datagram count
     */
    public int getTotalReadDatagrams() {
        return totalReadDatagrams.get();
    }

}
