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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
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

    private final InetSocketAddress bindBeforeConnectAddress;

    private final BufferOptions bufferOptions;

    private final DatagramFilters filters;

    private final DatagramClientCreation creationListener;

    private final DatagramClientDeletion deletionListener;

    private final boolean deferredListeners;

    private final State state;

    private DatagramInner inner;

    public DatagramCrusher(DatagramCrusherOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Options are not set");
        }

        options.validate();

        this.filters = new DatagramFilters(
            options.getIncomingTransformFilterFactory(),
            options.getOutgoingTransformFilterFactory(),
            options.getIncomingPassFilterFactory(),
            options.getOutgoingPassFilterFactory(),
            options.getIncomingGlobalThrottler(),
            options.getOutgoingThrottlerFactory()
        );

        this.reactor = options.getReactor();
        this.bindAddress = options.getBindAddress();
        this.connectAddress = options.getConnectAddress();
        this.bindBeforeConnectAddress = options.getBindBeforeConnectAddress();
        this.socketOptions = options.getSocketOptions().copy();
        this.bufferOptions = options.getBufferOptions().copy();
        this.creationListener = options.getCreationListener();
        this.deletionListener = options.getDeletionListener();
        this.deferredListeners = options.isDeferredListeners();

        this.state = new State(State.CLOSED);
    }

    void notifyOuterCreated(DatagramOuter outer) {
        if (creationListener != null) {
            Runnable r = () -> creationListener.created(outer.getClientAddress());

            reactor.getScheduler().executeListener(r, deferredListeners);
        }
    }

    void notifyOuterDeleted(DatagramOuter outer) {
        if (deletionListener != null) {
            Runnable r = () -> deletionListener.deleted(outer.getClientAddress(),
                outer.getByteMeters(), outer.getPacketMeters());

            reactor.getScheduler().executeListener(r, deferredListeners);
        }
    }

    @Override
    public void open() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.CLOSED)) {
                this.inner = new DatagramInner(this,
                    reactor, socketOptions, bufferOptions, filters,
                    bindAddress, connectAddress, bindBeforeConnectAddress);
                this.inner.unfreeze();

                LOGGER.info("DatagramCrusher <{}>-<{}> is started", bindAddress, connectAddress);

                state.set(State.OPEN);

                return true;
            } else {
                throw new IllegalStateException("DatagramCrusher is already open");
            }
        });
    }

    @Override
    public void close() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                this.inner.close();
                this.inner = null;

                state.set(State.CLOSED);

                LOGGER.info("DatagramCrusher <{}>-<{}> is closed", bindAddress, connectAddress);

                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    public void reopen() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                close();
                open();

                return true;
            } else {
                throw new IllegalStateException("DatagramCrusher is not open");
            }
        });
    }

    @Override
    public boolean isOpen() {
        return state.isAnyOf(State.OPEN | State.FROZEN);
    }

    @Override
    public void freeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.OPEN)) {
                if (!inner.isFrozen()) {
                    inner.freeze();
                }

                state.set(State.FROZEN);

                return true;
            } else {
                throw new IllegalStateException("DatagramСrusher is not open on freeze");
            }
        });
    }

    @Override
    public void unfreeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.FROZEN)) {
                if (inner.isFrozen()) {
                    inner.unfreeze();
                }

                state.set(State.OPEN);

                return true;
            } else {
                throw new IllegalStateException("DatagramCrusher is not frozen on unfreeze");
            }
        });
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
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return inner.getOuters().stream()
                    .map(DatagramOuter::getClientAddress)
                    .collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        });
    }

    @Override
    public RateMeters getClientByteMeters(InetSocketAddress clientAddress) {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                DatagramOuter outer = inner.getOuter(clientAddress);
                if (outer != null) {
                    return outer.getByteMeters();
                }
            }

            return null;
        });
    }

    /**
     * Get client packet meters
     * @param clientAddress Client address
     * @return Rate meters or null
     */
    public RateMeters getClientPacketMeters(InetSocketAddress clientAddress) {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                DatagramOuter outer = inner.getOuter(clientAddress);
                if (outer != null) {
                    return outer.getPacketMeters();
                }
            }

            return null;
        });
    }

    /**
     * Get inner socket byte meters
     * @return Rate meters or null
     */
    public RateMeters getInnerByteMeters() {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return inner.getByteMeters();
            } else {
                return null;
            }
        });
    }

    /**
     * Get inner socket packet meters
     * @return Rate meters or null
     */
    public RateMeters getInnerPacketMeters() {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return inner.getPacketMeters();
            } else {
                return null;
            }
        });
    }

    @Override
    public boolean closeClient(InetSocketAddress clientAddress) {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return inner.closeOuter(clientAddress);
            } else {
                return false;
            }
        });
    }

    /**
     * Close idle clients
     * @param maxIdleDuration Maximum allowed idle time
     * @param timeUnit Time unit of idle time
     * @return Number of closed clients
     */
    public int closeIdleClients(long maxIdleDuration, TimeUnit timeUnit) {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return inner.closeIdleOuters(timeUnit.toMillis(maxIdleDuration));
            } else {
                return 0;
            }
        });
    }

    @Override
    public int getClientTotalCount() {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return inner.getClientTotalCount();
            } else {
                return 0;
            }
        });
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
