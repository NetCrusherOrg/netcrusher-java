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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatagramInner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramInner.class);

    private final DatagramCrusher crusher;

    private final NioReactor reactor;

    private final DatagramCrusherSocketOptions socketOptions;

    private final ByteBufferFilterRepository filters;

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    private final long maxIdleDurationMs;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private final Map<InetSocketAddress, DatagramOuter> outers;

    private final DatagramQueue pending;

    private boolean opened;

    private volatile boolean frozen;

    public DatagramInner(DatagramCrusher crusher,
                         NioReactor reactor,
                         DatagramCrusherSocketOptions socketOptions,
                         ByteBufferFilterRepository filters,
                         InetSocketAddress localAddress,
                         InetSocketAddress remoteAddress,
                         long maxIdleDurationMs) throws IOException {
        this.crusher = crusher;
        this.reactor = reactor;
        this.filters = filters;
        this.socketOptions = socketOptions;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.outers = new ConcurrentHashMap<>(32);
        this.pending = new DatagramQueue();
        this.maxIdleDurationMs = maxIdleDurationMs;
        this.frozen = true;
        this.opened = true;

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        socketOptions.setupSocketChannel(this.channel);
        this.channel.bind(localAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = reactor.registerSelector(channel, 0, this::callback);

        LOGGER.debug("Inner on <{}> is started", localAddress);
    }

    protected synchronized void unfreeze() throws IOException {
        if (opened) {
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

    protected synchronized void freeze() throws IOException {
        if (opened) {
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

    protected boolean isFrozen() {
        return frozen;
    }

    protected synchronized void close() throws IOException {
        if (opened) {
            freeze();

            if (!pending.isEmpty()) {
                LOGGER.warn("On closing inner has {} incoming datagrams with {} bytes in total",
                    pending.size(), pending.bytes());
            }

            outers.values().forEach(DatagramOuter::close);
            outers.clear();

            NioUtils.closeChannel(channel);

            reactor.wakeupSelector();

            opened = false;

            LOGGER.debug("Inner on <{}> is closed", localAddress);
        }
    }

    protected void closeInternal() {
        reactor.execute(() -> {
            crusher.close();
            return null;
        });
    }

    protected void closeOuter(InetSocketAddress clientAddress) {
        DatagramOuter outer = outers.remove(clientAddress);
        if (outer != null) {
            outer.close();
        }
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
            int written = channel.send(entry.getBuffer(), entry.getAddress());
            if (written == 0) {
                pending.retry(entry);
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send {} bytes to client <{}>", written, entry.getAddress());
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

            List<ByteBufferFilter> incomingFilters = filters.getIncoming().createFilters(address);
            List<ByteBufferFilter> outgoingFilters = filters.getOutgoing().createFilters(address);

            outer = new DatagramOuter(this, reactor, socketOptions,
                incomingFilters, outgoingFilters,
                address, remoteAddress);
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
                    outer.close();
                    outerIterator.remove();
                }
            }

            int countAfter = outers.size();
            if (countAfter < countBefore) {
                LOGGER.debug("Outer connections are cleared ({} -> {})", countBefore, countAfter);
            }
        }
    }

    protected void enqueue(InetSocketAddress address, ByteBuffer bbToCopy) {
        boolean added = pending.add(address, bbToCopy);
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

}
