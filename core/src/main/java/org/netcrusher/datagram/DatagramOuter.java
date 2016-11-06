package org.netcrusher.datagram;

import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.meter.RateMeterImpl;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.nio.NioUtils;
import org.netcrusher.core.nio.SelectionKeyControl;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
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

    private final DatagramQueue incoming;

    private final DatagramChannel channel;

    private final SelectionKeyControl selectionKeyControl;

    private final Meters meters;

    private final Filters filters;

    private final ByteBuffer bb;

    private final State state;

    private volatile long lastOperationTimestamp;

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

        this.meters = new Meters();
        this.filters = new Filters(filters, clientAddress);

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        socketOptions.setupSocketChannel(this.channel);
        // Connected DatagramChannel doesn't work with empty datagrams
        // https://bugs.openjdk.java.net/browse/JDK-8013175
        // this.channel.connect(connectAddress);
        this.channel.configureBlocking(false);
        bufferOptions.checkDatagramSocket(channel.socket());

        this.bb = NioUtils.allocaleByteBuffer(channel.socket().getReceiveBufferSize(), bufferOptions.isDirect());

        SelectionKey selectionKey = reactor.getSelector().register(channel, 0, this::callback);
        this.selectionKeyControl = new SelectionKeyControl(selectionKey);

        this.state = new State(State.FROZEN);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Outer for <{}> to <{}> is started", clientAddress, connectAddress);
        }
    }

    void close() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                if (!incoming.isEmpty()) {
                    LOGGER.warn("On closing outer has {} incoming datagrams", incoming.size());
                }

                NioUtils.close(channel);

                state.set(State.CLOSED);

                LOGGER.debug("Outer for <{}> to <{}> is closed", clientAddress, connectAddress);

                return true;
            } else {
                return false;
            }
        });
    }

    private void closeAll() {
        this.close();
        inner.closeOuter(clientAddress);
    }

    void unfreeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.FROZEN)) {
                if (incoming.isEmpty()) {
                    selectionKeyControl.setReadsOnly();
                } else {
                    selectionKeyControl.setAll();
                }

                state.set(State.OPEN);

                return true;
            } else {
                return false;
            }
        });
    }

    void freeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.OPEN)) {
                if (selectionKeyControl.isValid()) {
                    selectionKeyControl.setNone();
                }

                state.set(State.FROZEN);

                return true;
            } else {
                return false;
            }
        });
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isWritable()) {
            try {
                handleWritableEvent(false);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on write");
                closeAll();
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port <{}> is unreachable on write", connectAddress);
                closeAll();
            } catch (UnresolvedAddressException e) {
                LOGGER.error("Connect address <{}> is unresolved", connectAddress);
                closeAll();
            } catch (Exception e) {
                LOGGER.error("Exception in outer on write", e);
                closeAll();
            }
        }

        if (selectionKey.isReadable()) {
            try {
                handleReadableEvent();
            } catch (ClosedChannelException e) {
                LOGGER.debug("Channel is closed on read");
                closeAll();
            } catch (EOFException e) {
                LOGGER.debug("EOF on read");
                closeAll();
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port <{}> is unreachable on read", connectAddress);
                closeAll();
            } catch (Exception e) {
                LOGGER.error("Exception in outer on read", e);
                closeAll();
            }
        }
    }

    private void handleWritableEvent(boolean forced) throws IOException {
        int count = 0;
        while (state.isWritable()) {
            final DatagramQueue.BufferEntry entry = incoming.request();
            if (entry == null) {
                break;
            }

            final long delayNs = entry.getScheduledNs() - System.nanoTime();
            if (delayNs > 0) {
                throttleSend(delayNs);
                incoming.retry(entry);
                break;
            }

            final boolean emptyDatagram = !entry.getBuffer().hasRemaining();
            if (emptyDatagram && (count > 0 || forced)) {
                // due to NIO API problem we can't make a difference between two cases:
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

                meters.sentBytes.update(sent);
                meters.sentPackets.increment();

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
            selectionKeyControl.disableWrites();
        }
    }

    private void handleReadableEvent() throws IOException {
        while (state.isReadable()) {
            bb.clear();

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

            meters.readBytes.update(read);
            meters.readPackets.increment();

            final boolean passed = filter(bb, filters.incomingPassFilter, filters.incomingTransferFilter);
            if (passed) {
                inner.enqueue(clientAddress, bb);
            }

            lastOperationTimestamp = System.currentTimeMillis();
        }
    }

    private void suggestDeferredSent() {
        if (!incoming.isEmpty() && state.isWritable()) {
            selectionKeyControl.enableWrites();
        }
    }

    private void suggestImmediateSent() throws IOException {
        if (!incoming.isEmpty() && state.isWritable()) {
            handleWritableEvent(true);
        }
    }

    void enqueue(ByteBuffer bbToCopy) throws IOException {
        final boolean passed = filter(bbToCopy, filters.outgoingPassFilter, filters.outgoingTransferFilter);
        if (passed) {
            final Throttler throttler = filters.outgoingThrottler;

            final long delayNs;
            if (throttler != null) {
                delayNs = throttler.calculateDelayNs(bbToCopy);
            } else {
                delayNs = Throttler.NO_DELAY_NS;
            }

            incoming.add(this.connectAddress, bbToCopy, delayNs);
            suggestImmediateSent();
            suggestDeferredSent();
        }
    }

    private boolean filter(ByteBuffer bbToCopy, PassFilter passFilter, TransformFilter transformFilter) {
        if (passFilter != null) {
            final boolean passed = passFilter.check(bbToCopy);
            if (!passed) {
                return false;
            }
        }

        if (transformFilter != null) {
            transformFilter.transform(bbToCopy);
        }

        return true;
    }

    private void throttleSend(long delayNs) {
        if (this.state.is(State.OPEN) && !this.state.isSendThrottled()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Outer sent is throttled on {}ns", delayNs);
            }

            this.state.setSendThrottled(true);

            if (this.selectionKeyControl.isValid()) {
                this.selectionKeyControl.disableWrites();
            }

            reactor.getSelector().schedule(this::unthrottleSend, delayNs);
        }
    }

    private void unthrottleSend() {
        if (this.state.is(State.OPEN) && this.state.isSendThrottled()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Outer sent is unthrottled");
            }

            this.state.setSendThrottled(false);

            if (this.selectionKeyControl.isValid() && state.isWritable() && !incoming.isEmpty()) {
                this.selectionKeyControl.enableWrites();
            }
        }
    }

    InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    long getIdleDurationMs() {
        return System.currentTimeMillis() - lastOperationTimestamp;
    }

    RateMeters getByteMeters() {
        return new RateMeters(meters.readBytes, meters.sentBytes);
    }

    RateMeters getPacketMeters() {
        return new RateMeters(meters.readPackets, meters.sentPackets);
    }

    private static final class State extends BitState {

        private static final int OPEN = bit(0);

        private static final int FROZEN = bit(1);

        private static final int CLOSED = bit(2);

        private boolean sendThrottled;

        private State(int state) {
            super(state);
            this.sendThrottled = false;
        }

        private boolean isWritable() {
            return is(OPEN) && !sendThrottled;
        }

        private boolean isReadable() {
            return is(OPEN);
        }

        private boolean isSendThrottled() {
            return sendThrottled;
        }

        private void setSendThrottled(boolean sendThrottled) {
            this.sendThrottled = sendThrottled;
        }
    }

    private static final class Meters {

        private final RateMeterImpl sentBytes;

        private final RateMeterImpl readBytes;

        private final RateMeterImpl sentPackets;

        private final RateMeterImpl readPackets;

        private Meters() {
            this.readBytes = new RateMeterImpl();
            this.sentBytes = new RateMeterImpl();
            this.readPackets = new RateMeterImpl();
            this.sentPackets = new RateMeterImpl();
        }
    }

    private static final class Filters {

        private final TransformFilter outgoingTransferFilter;

        private final TransformFilter incomingTransferFilter;

        private final PassFilter outgoingPassFilter;

        private final PassFilter incomingPassFilter;

        private final Throttler outgoingThrottler;

        private Filters(DatagramFilters filters, InetSocketAddress clientAddress) {
            if (filters.getOutgoingTransformFilterFactory() != null) {
                this.outgoingTransferFilter = filters.getOutgoingTransformFilterFactory().allocate(clientAddress);
            } else {
                this.outgoingTransferFilter = null;
            }

            if (filters.getIncomingTransformFilterFactory() != null) {
                this.incomingTransferFilter = filters.getIncomingTransformFilterFactory().allocate(clientAddress);
            } else {
                this.incomingTransferFilter = null;
            }

            if (filters.getOutgoingPassFilterFactory() != null) {
                this.outgoingPassFilter = filters.getOutgoingPassFilterFactory().allocate(clientAddress);
            } else {
                this.outgoingPassFilter = null;
            }

            if (filters.getIncomingPassFilterFactory() != null) {
                this.incomingPassFilter = filters.getIncomingPassFilterFactory().allocate(clientAddress);
            } else {
                this.incomingPassFilter = null;
            }

            if (filters.getOutgoingThrottlerFactory() != null) {
                this.outgoingThrottler = filters.getOutgoingThrottlerFactory().allocate(clientAddress);
            } else {
                this.outgoingThrottler = null;
            }
        }

    }
}
