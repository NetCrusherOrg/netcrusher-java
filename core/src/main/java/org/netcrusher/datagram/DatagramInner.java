package org.netcrusher.datagram;

import org.netcrusher.common.NioReactor;
import org.netcrusher.common.NioUtils;
import org.netcrusher.filter.ByteBufferFilter;
import org.netcrusher.filter.ByteBufferFilterRepository;
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

    private final DatagramQueue pending;

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
        this.pending = new DatagramQueue();
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

        this.selectionKey = reactor.registerSelector(channel, 0, this::callback);

        LOGGER.debug("Inner on <{}> is started", bindAddress);
    }

    synchronized void unfreeze() throws IOException {
        if (open) {
            if (frozen) {
                reactor.executeSelectorOp(() -> {
                    int ops = pending.isEmpty() ?
                        SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    selectionKey.interestOps(ops);

                    outers.values().forEach(DatagramOuter::unfreeze);
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
                reactor.executeSelectorOp(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(0);
                    }

                    outers.values().forEach(DatagramOuter::freeze);
                });

                frozen = true;
            }
        } else {
            throw new IllegalStateException("Inner is closed");
        }
    }

    synchronized void closeExternal() throws IOException {
        if (open) {
            freeze();

            if (pending.bytes() > 0) {
                LOGGER.warn("On closing inner has {} incoming datagrams with {} bytes in total",
                    pending.size(), pending.bytes());
            }

            outers.values().forEach(DatagramOuter::closeExternal);
            outers.clear();

            NioUtils.closeChannel(channel);

            reactor.wakeupSelector();

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
        reactor.execute(() -> {
            crusher.close();
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
            } catch (IOException e) {
                LOGGER.error("Exception in inner on read", e);
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
                LOGGER.error("Exception in inner on write", e);
                closeInternal();
            }
        }
    }

    private void handleWritable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        DatagramQueue.Entry entry;
        while ((entry = pending.request()) != null) {
            int sent = channel.send(entry.getBuffer(), entry.getAddress());
            if (sent == 0) {
                pending.retry(entry);
                break;
            }

            totalSentBytes.addAndGet(sent);
            totalSentDatagrams.incrementAndGet();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send {} bytes to client <{}>", sent, entry.getAddress());
            }

            if (entry.getBuffer().hasRemaining()) {
                LOGGER.warn("Datagram is splitted");
                pending.retry(entry);
            } else {
                pending.release(entry);
            }
        }

        if (pending.isEmpty()) {
            NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    private void handleReadable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        while (true) {
            InetSocketAddress address = (InetSocketAddress) channel.receive(bb);
            if (address == null) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received {} bytes from inner <{}>", bb.limit(), address);
            }

            DatagramOuter outer = requestOuter(address);

            bb.flip();

            totalReadBytes.addAndGet(bb.limit());
            totalReadDatagrams.incrementAndGet();

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
        boolean added = pending.add(address, bbToCopy);
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    boolean isFrozen() {
        return frozen;
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
