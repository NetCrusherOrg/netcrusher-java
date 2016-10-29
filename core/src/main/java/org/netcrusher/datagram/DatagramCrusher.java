package org.netcrusher.datagram;

import org.netcrusher.NetCrusher;
import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
import org.netcrusher.datagram.callback.DatagramClientCreation;
import org.netcrusher.datagram.callback.DatagramClientDeletion;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * <p>DatagramCrusher - a UDP proxy for test purposes. To create a new instance use DatagramCrusherBuilder</p>
 *
 * <pre>
 * NioReactor reactor = new NioReactor();
 * DatagramCrusher crusher = DatagramCrusherBuilder.builder()
 *     .withReactor(reactor)
 *     .withLocalAddress("localhost", 10081)
 *     .withRemoteAddress("time-nw.nist.gov", 37)
 *     .buildAndOpen();
 *
 * // ... do something
 *
 * crusher.close();
 * reactor.close();
 * </pre>
 *
 * @see TcpCrusherBuilder
 * @see NioReactor
 */
public class DatagramCrusher implements NetCrusher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramCrusher.class);

    private final NioReactor reactor;

    private final DatagramCrusherSocketOptions socketOptions;

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final BufferOptions bufferOptions;

    private final DatagramFilters filters;

    private final AtomicInteger clientTotalCount;

    private final DatagramClientCreation creationListener;

    private final DatagramClientDeletion deletionListener;

    private final State state;

    private DatagramInner inner;

    public DatagramCrusher(
            NioReactor reactor,
            InetSocketAddress bindAddress,
            InetSocketAddress connectAddress,
            DatagramCrusherSocketOptions socketOptions,
            DatagramFilters filters,
            DatagramClientCreation creationListener,
            DatagramClientDeletion deletionListener,
            BufferOptions bufferOptions)
    {
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.socketOptions = socketOptions;
        this.reactor = reactor;
        this.state = new State(State.CLOSED);
        this.filters = filters;
        this.bufferOptions = bufferOptions;
        this.clientTotalCount = new AtomicInteger(0);
        this.creationListener = creationListener;
        this.deletionListener = deletionListener;
    }

    void notifyOuterCreated(DatagramOuter outer) {
        clientTotalCount.incrementAndGet();

        if (creationListener != null) {
            reactor.getScheduler().execute(() ->
                creationListener.created(outer.getClientAddress()));
        }
    }

    void notifyOuterDeleted(DatagramOuter outer) {
        if (deletionListener != null) {
            reactor.getScheduler().execute(() ->
                deletionListener.deleted(outer.getClientAddress(), outer.getByteMeters(), outer.getPacketMeters()));
        }
    }

    @Override
    public void open() throws IOException {
        if (state.lockIf(State.CLOSED)) {
            try {
                this.inner = new DatagramInner(this,
                    reactor, socketOptions, filters, bindAddress, connectAddress, bufferOptions);
                this.inner.unfreeze();

                clientTotalCount.set(0);

                LOGGER.info("DatagramCrusher <{}>-<{}> is started", bindAddress, connectAddress);

                state.set(State.OPEN);
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("DatagramCrusher is already open");
        }
    }

    @Override
    public void close() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                this.inner.close();
                this.inner = null;

                state.set(State.CLOSED);

                LOGGER.info("DatagramCrusher <{}>-<{}> is closed", bindAddress, connectAddress);
            } finally {
                state.unlock();
            }
        }
    }

    @Override
    public void reopen() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                close();
                open();
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("DatagramCrusher is not open");
        }
    }

    @Override
    public boolean isOpen() {
        return state.isAnyOf(State.OPEN | State.FROZEN);
    }

    @Override
    public void freeze() throws IOException {
        if (state.lockIf(State.OPEN)) {
            try {
                if (!inner.isFrozen()) {
                    inner.freeze();
                }

                state.set(State.FROZEN);
            } finally {
                state.unlock();
            }
        } else {
            if (!isFrozen()) {
                throw new IllegalStateException("DatagramСrusher is not finally frozen: " + state);
            }
        }
    }

    @Override
    public void unfreeze() throws IOException {
        if (state.lockIf(State.FROZEN)) {
            try {
                if (inner.isFrozen()) {
                    inner.unfreeze();
                }

                state.set(State.OPEN);
            } finally {
                state.unlock();
            }
        } else {
            if (isFrozen()) {
                throw new IllegalStateException("DatagramCrusher is not finally unfrozen: " + state);
            }
        }
    }

    @Override
    public boolean isFrozen() {
        return state.isAnyOf(State.FROZEN | State.CLOSED);
    }

    @Override
    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    @Override
    public InetSocketAddress getConnectAddress() {
        return connectAddress;
    }

    @Override
    public Collection<InetSocketAddress> getClientAddresses() {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                return inner.getOuters().stream()
                    .map(DatagramOuter::getClientAddress)
                    .collect(Collectors.toList());
            } finally {
                state.unlock();
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public RateMeters getClientByteMeters(InetSocketAddress clientAddress) {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                DatagramOuter outer = inner.getOuter(clientAddress);
                if (outer != null) {
                    return outer.getByteMeters();
                }
            } finally {
                state.unlock();
            }
        }

        return null;
    }

    /**
     * Get client packet meters
     * @param clientAddress Client address
     * @return Rate meters
     */
    public RateMeters getClientPacketMeters(InetSocketAddress clientAddress) {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                DatagramOuter outer = inner.getOuter(clientAddress);
                if (outer != null) {
                    return outer.getPacketMeters();
                }
            } finally {
                state.unlock();
            }
        }

        return null;
    }

    /**
     * Get inner socket byte meters
     * @return Rate meters
     */
    public RateMeters getInnerByteMeters() {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                return inner.getByteMeters();
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("DatagramCrusher is closed");
        }
    }

    /**
     * Get inner socket packet meters
     * @return Rate meters
     */
    public RateMeters getInnerPacketMeters() {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                return inner.getPacketMeters();
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("DatagramCrusher is closed");
        }
    }

    @Override
    public boolean closeClient(InetSocketAddress clientAddress) throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                return inner.closeOuter(clientAddress);
            } finally {
                state.unlock();
            }
        }

        return false;
    }

    /**
     * Close idle clients
     * @param maxIdleDuration Maximum allowed idle time
     * @param timeUnit Time unit of idle time
     * @throws IOException Exception on error
     * @return Number of closed clients
     */
    public int closeIdleClients(long maxIdleDuration, TimeUnit timeUnit) throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                return inner.closeIdleOuters(timeUnit.toMillis(maxIdleDuration));
            } finally {
                state.unlock();
            }
        }

        return 0;
    }

    @Override
    public int getClientTotalCount() {
        return clientTotalCount.get();
    }

    private static final class State extends BitState {

        private static final int OPEN = bit(0);

        private static final int FROZEN = bit(1);

        private static final int CLOSED = bit(2);

        private State(int state) {
            super(state);
        }
    }

}
