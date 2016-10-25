package org.netcrusher.datagram;

import org.netcrusher.core.NioUtils;
import org.netcrusher.core.meter.RateMeterImpl;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class DatagramInner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramInner.class);

    private final DatagramCrusher crusher;

    private final NioReactor reactor;

    private final DatagramCrusherSocketOptions socketOptions;

    private final DatagramFilters filters;

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private final Map<InetSocketAddress, DatagramOuter> outers;

    private final DatagramQueue incoming;

    private final RateMeterImpl sentByteMeter;

    private final RateMeterImpl readByteMeter;

    private final RateMeterImpl sentPacketMeter;

    private final RateMeterImpl readPacketMeter;

    private final int queueLimit;

    private boolean open;

    private volatile boolean frozen;

    DatagramInner(
            DatagramCrusher crusher,
            NioReactor reactor,
            DatagramCrusherSocketOptions socketOptions,
            DatagramFilters filters,
            InetSocketAddress bindAddress,
            InetSocketAddress connectAddress,
            int queueLimit) throws IOException
    {
        this.crusher = crusher;
        this.reactor = reactor;
        this.filters = filters;
        this.socketOptions = socketOptions;
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.outers = new ConcurrentHashMap<>(32);
        this.incoming = new DatagramQueue(queueLimit);
        this.queueLimit = queueLimit;
        this.frozen = true;
        this.open = true;

        this.sentByteMeter = new RateMeterImpl();
        this.readByteMeter = new RateMeterImpl();
        this.sentPacketMeter = new RateMeterImpl();
        this.readPacketMeter = new RateMeterImpl();

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
                reactor.getSelector().execute(() -> {
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
                reactor.getSelector().execute(() -> {
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

            Iterator<DatagramOuter> outerIterator = outers.values().iterator();
            while (outerIterator.hasNext()) {
                DatagramOuter outer = outerIterator.next();
                outerIterator.remove();

                outer.closeExternal();
                crusher.notifyOuterDeleted(outer);
            }

            NioUtils.closeChannel(channel);

            reactor.getSelector().wakeup();

            open = false;
            frozen = true;

            LOGGER.debug("Inner on <{}> is closed", bindAddress);
        }
    }

    private void closeInternal() {
        reactor.getScheduler().execute(() -> {
            crusher.close();
            return true;
        });
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isWritable()) {
            try {
                handleWritableEvent(false);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on write");
                closeInternal();
            } catch (IOException e) {
                LOGGER.error("Exception in inner on write", e);
                closeInternal();
            }
        }

        if (selectionKey.isReadable()) {
            try {
                handleReadableEvent();
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on read");
                closeInternal();
            } catch (IOException e) {
                LOGGER.error("Exception in inner on read", e);
                closeInternal();
            }
        }
    }

    void handleWritableEvent(boolean forced) throws IOException {
        DatagramQueue.BuffferEntry entry;
        int count = 0;
        while ((entry = incoming.request()) != null) {
            final boolean emptyDatagram = !entry.getBuffer().hasRemaining();
            if (emptyDatagram && (count > 0 || forced)) {
                // due to NIO API problem we can't differ between two cases:
                // - empty datagram is sent (send() returns 0)
                // - no free space in socket buffer (send() returns 0)
                // so we want an empty datagram to be sent first on OP_WRITE
                incoming.retry(entry);
                break;
            }

            final int sent;
            try {
                sent = channel.send(entry.getBuffer(), entry.getAddress());
            } catch (SocketException e) {
                DatagramUtils.rethrowSocketException(e);
                incoming.retry(entry);
                break;
            }

            if (emptyDatagram || sent > 0) {
                if (entry.getBuffer().hasRemaining()) {
                    LOGGER.warn("Datagram is split");
                    incoming.retry(entry);
                } else {
                    incoming.release(entry);
                }

                sentByteMeter.update(sent);
                sentPacketMeter.increment();

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

    private void handleReadableEvent() throws IOException {
        while (true) {
            final InetSocketAddress address = (InetSocketAddress) channel.receive(bb);
            if (address == null) {
                break;
            }

            bb.flip();
            final int read = bb.remaining();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received {} bytes from inner <{}>", read, address);
            }

            readByteMeter.update(read);
            readPacketMeter.increment();

            DatagramOuter outer = requestOuter(address);

            outer.enqueue(bb);

            // try to immediately sent the datagram
            if (outer.hasIncoming()) {
                outer.handleWritableEvent(true);
            }

            // if data still remains we raise the OP_WRITE flag
            if (outer.hasIncoming()) {
                outer.enableOperations(SelectionKey.OP_WRITE);
            }

            bb.clear();
        }
    }

    private DatagramOuter requestOuter(InetSocketAddress address) throws IOException {
        DatagramOuter outer = outers.get(address);

        if (outer == null) {
            outer = new DatagramOuter(this, reactor, socketOptions, filters, queueLimit,
                address, connectAddress);
            outer.unfreeze();

            outers.put(address, outer);

            crusher.notifyOuterCreated(outer);
        }

        return outer;
    }

    void enqueue(InetSocketAddress address, ByteBuffer bbToCopy, long delayNs) {
        incoming.add(address, bbToCopy, delayNs);
    }

    boolean closeOuter(InetSocketAddress clientAddress) {
        DatagramOuter outer = outers.remove(clientAddress);
        if (outer != null) {
            outer.closeExternal();
            crusher.notifyOuterDeleted(outer);

            return true;
        } else {
            return false;
        }
    }

    int closeIdleOuters(long maxIdleDurationMs) {
        int countBefore = outers.size();
        if (countBefore > 0) {
            Iterator<DatagramOuter> outerIterator = outers.values().iterator();

            while (outerIterator.hasNext()) {
                DatagramOuter outer = outerIterator.next();

                if (outer.getIdleDurationMs() > maxIdleDurationMs) {
                    outerIterator.remove();

                    outer.closeExternal();
                    crusher.notifyOuterDeleted(outer);
                }
            }

            int countAfter = outers.size();

            return countBefore - countAfter;
        } else {
            return 0;
        }
    }

    boolean hasIncoming() {
        return !incoming.isEmpty();
    }

    void enableOperations(int operations) {
        if (selectionKey.isValid()) {
            NioUtils.setupInterestOps(selectionKey, operations);
        }
    }

    DatagramOuter getOuter(InetSocketAddress clientAddress) {
        return outers.get(clientAddress);
    }

    Collection<DatagramOuter> getOuters() {
        return outers.values();
    }

    RateMeters getByteMeters() {
        return new RateMeters(readByteMeter, sentByteMeter);
    }

    RateMeters getPacketMeters() {
        return new RateMeters(readPacketMeter, sentPacketMeter);
    }
}
