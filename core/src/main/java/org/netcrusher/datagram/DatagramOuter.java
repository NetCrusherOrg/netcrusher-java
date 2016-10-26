package org.netcrusher.datagram;

import org.netcrusher.core.NioUtils;
import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.meter.RateMeterImpl;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.Throttler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.UnresolvedAddressException;

class DatagramOuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramOuter.class);

    private final DatagramInner inner;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private final InetSocketAddress connectAddress;

    private final DatagramFilters filters;

    private final DatagramQueue incoming;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private final RateMeterImpl sentByteMeter;

    private final RateMeterImpl readByteMeter;

    private final RateMeterImpl sentPacketMeter;

    private final RateMeterImpl readPacketMeter;

    private boolean open;

    private volatile boolean frozen;

    private long lastOperationTimestamp;

    DatagramOuter(
            DatagramInner inner,
            NioReactor reactor,
            DatagramCrusherSocketOptions socketOptions,
            DatagramFilters filters,
            BufferOptions bufferOptions,
            InetSocketAddress clientAddress,
            InetSocketAddress connectAddress) throws IOException
    {
        this.inner = inner;
        this.reactor = reactor;
        this.clientAddress = clientAddress;
        this.connectAddress = connectAddress;
        this.incoming = new DatagramQueue(bufferOptions);
        this.lastOperationTimestamp = System.currentTimeMillis();
        this.frozen = true;
        this.open = true;

        this.readByteMeter = new RateMeterImpl();
        this.sentByteMeter = new RateMeterImpl();
        this.readPacketMeter = new RateMeterImpl();
        this.sentPacketMeter = new RateMeterImpl();

        this.filters = filters;

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        socketOptions.setupSocketChannel(this.channel);
        // Connected DatagramChannel doesn't work with empty datagrams
        // https://bugs.openjdk.java.net/browse/JDK-8013175
        // this.channel.connect(connectAddress);
        this.channel.configureBlocking(false);
        bufferOptions.checkDatagramSocket(channel.socket());

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
            LOGGER.debug("Component is closed on freeze");
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
            frozen = true;

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
        if (selectionKey.isWritable()) {
            try {
                handleWritableEvent(false);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on write");
                closeInternal();
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port <{}> is unreachable on write", connectAddress);
                closeInternal();
            } catch (UnresolvedAddressException e) {
                LOGGER.error("Connect address <{}> is unresolved", connectAddress);
                closeInternal();
            } catch (IOException e) {
                LOGGER.error("Exception in outer on write", e);
                closeInternal();
            }
        }

        if (selectionKey.isReadable()) {
            try {
                handleReadableEvent();
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on read");
                closeInternal();
            } catch (EOFException e) {
                LOGGER.debug("EOF on read");
                closeInternal();
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port <{}> is unreachable on read", connectAddress);
                closeInternal();
            } catch (IOException e) {
                LOGGER.error("Exception in outer on read", e);
                closeInternal();
            }
        }
    }

    void handleWritableEvent(boolean forced) throws IOException {
        DatagramQueue.BufferEntry entry;
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
                lastOperationTimestamp = System.currentTimeMillis();
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
            final SocketAddress address = channel.receive(bb);
            if (address == null) {
                break;
            }

            if (!connectAddress.equals(address)) {
                LOGGER.trace("Datagram from non-connect address <{}> will be dropped", address);
                continue;
            }

            bb.flip();
            final int read = bb.remaining();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read {} bytes from outer for <{}>", read, clientAddress);
            }

            readByteMeter.update(read);
            readPacketMeter.increment();

            final boolean passed = filter(bb, filters.getIncomingTransformFilter(), filters.getIncomingPassFilter());
            if (passed) {
                final Throttler throttler = filters.getIncomingThrottler();

                final long delayNs;
                if (throttler != null) {
                    delayNs = throttler.calculateDelayNs(clientAddress, bb);
                } else {
                    delayNs = Throttler.NO_DELAY_NS;
                }

                inner.enqueue(clientAddress, bb, delayNs);

                // try to immediately sent the datagram
                if (inner.hasIncoming()) {
                    inner.handleWritableEvent(true);
                }
            }

            bb.clear();

            lastOperationTimestamp = System.currentTimeMillis();
        }

        // if data still remains we raise the OP_WRITE flag
        if (inner.hasIncoming()) {
            inner.enableOperations(SelectionKey.OP_WRITE);
        }
    }

    void enqueue(ByteBuffer bb) {
        final boolean passed = filter(bb, filters.getOutgoingTransformFilter(), filters.getOutgoingPassFilter());
        if (passed) {
            final Throttler throttler = filters.getOutgoingThrottler();

            final long delayNs;
            if (throttler != null) {
                delayNs = throttler.calculateDelayNs(clientAddress, bb);
            } else {
                delayNs = Throttler.NO_DELAY_NS;
            }

            incoming.add(connectAddress, bb, delayNs);
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

    private boolean filter(ByteBuffer bb, TransformFilter transformFilter, PassFilter passFilter) {
        if (passFilter != null) {
            final boolean passed = passFilter.check(clientAddress, bb);
            if (!passed) {
                return false;
            }
        }

        if (transformFilter != null) {
            transformFilter.transform(clientAddress, bb);
        }

        return true;
    }

    InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    long getIdleDurationMs() {
        return System.currentTimeMillis() - lastOperationTimestamp;
    }

    RateMeters getByteMeters() {
        return new RateMeters(readByteMeter, sentByteMeter);
    }

    RateMeters getPacketMeters() {
        return new RateMeters(readPacketMeter, sentPacketMeter);
    }
}



