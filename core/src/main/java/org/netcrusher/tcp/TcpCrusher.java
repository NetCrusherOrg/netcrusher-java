package org.netcrusher.tcp;

import org.netcrusher.NetCrusher;
import org.netcrusher.NetFreezer;
import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
import org.netcrusher.tcp.callback.TcpClientCreation;
import org.netcrusher.tcp.callback.TcpClientDeletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * <p>TcpCrusher - a TCP proxy for test purposes. To create a new instance use TcpCrusherBuilder</p>
 *
 * <pre>
 * NioReactor reactor = new NioReactor();
 * TcpCrusher crusher = TcpCrusherBuilder.builder()
 *     .withReactor(reactor)
 *     .withLocalAddress("localhost", 10080)
 *     .withRemoteAddress("google.com", 80)
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
public class TcpCrusher implements NetCrusher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpCrusher.class);

    private static final int DEFAULT_PAIR_CAPACITY = 32;

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final TcpCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final Map<InetSocketAddress, TcpPair> pairs;

    private final TcpClientCreation creationListener;

    private final TcpClientDeletion deletionListener;

    private final boolean deferredListeners;

    private final BufferOptions bufferOptions;

    private final TcpFilters filters;

    private final AtomicInteger clientTotalCount;

    private final State state;

    private TcpAcceptor acceptor;

    public TcpCrusher(
        NioReactor reactor,
        InetSocketAddress bindAddress,
        InetSocketAddress connectAddress,
        TcpCrusherSocketOptions socketOptions,
        TcpFilters filters,
        TcpClientCreation creationListener,
        TcpClientDeletion deletionListener,
        boolean deferredListeners,
        BufferOptions bufferOptions)
    {
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.reactor = reactor;
        this.socketOptions = socketOptions;
        this.pairs = new ConcurrentHashMap<>(DEFAULT_PAIR_CAPACITY);
        this.filters = filters;
        this.bufferOptions = bufferOptions;
        this.creationListener = creationListener;
        this.deletionListener = deletionListener;
        this.deferredListeners = deferredListeners;
        this.clientTotalCount = new AtomicInteger(0);
        this.state = new State(State.CLOSED);
    }

    void notifyPairCreated(TcpPair pair) {
        LOGGER.debug("Pair is created for <{}>", pair.getClientAddress());

        clientTotalCount.incrementAndGet();

        pairs.put(pair.getClientAddress(), pair);

        if (creationListener != null) {
            Runnable r = () -> creationListener.created(pair.getClientAddress());

            reactor.getScheduler().executeListener(r, deferredListeners);
        }
    }

    private void notifyPairDeleted(TcpPair pair) {
        if (deletionListener != null) {
            Runnable r = () -> deletionListener.deleted(pair.getClientAddress(), pair.getByteMeters());

            reactor.getScheduler().executeListener(r, deferredListeners);
        }
    }

    @Override
    public void open() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.CLOSED)) {
                this.acceptor = new TcpAcceptor(this, reactor, bindAddress, connectAddress, socketOptions,
                    filters, bufferOptions);

                clientTotalCount.set(0);

                state.set(State.FROZEN);

                LOGGER.info("TcpCrusher <{}>-<{}> is open", bindAddress, connectAddress);

                unfreeze();

                return true;
            } else {
                throw new IllegalStateException("TcpCrusher is already open");
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

                closeAllPairs();

                acceptor.close();
                acceptor = null;

                state.set(State.CLOSED);

                LOGGER.info("TcpCrusher <{}>-<{}> is closed", bindAddress, connectAddress);

                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    public boolean isOpen() {
        return state.isAnyOf(State.OPEN | State.FROZEN);
    }

    /**
     * Close all pairs but keeps listening socket open
     */
    public void closeAllPairs() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                for (TcpPair pair : pairs.values()) {
                    pair.close();
                    notifyPairDeleted(pair);
                }

                pairs.clear();

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
                throw new IllegalStateException("TcpCrusher is already closed");
            }
        });
    }

    /**
     * Freezes crusher proxy. Call freeze() on all pairs and freezes the acceptor
     * @see TcpCrusher#freezeAllPairs()
     * @see TcpPair#freeze()
     */
    @Override
    public void freeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.OPEN)) {
                if (!acceptor.isFrozen()) {
                    acceptor.freeze();
                }

                freezeAllPairs();

                state.set(State.FROZEN);

                return true;
            } else {
                throw new IllegalStateException("TcpCrusher is not open on freeze");
            }
        });
    }

    /**
     * Freezes all TCP pairs
     */
    public void freezeAllPairs() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                for (TcpPair pair : pairs.values()) {
                    if (!pair.isFrozen()) {
                        pair.freeze();
                    }
                }

                return true;
            } else {
                throw new IllegalStateException("TcpCrusher is closed");
            }
        });
    }

    /**
     * Unfreezes the crusher. Call unfreeze() on all pairs and unfreezes the acceptor
     * @see TcpCrusher#unfreezeAllPairs()
     * @see TcpPair#unfreeze()
     */
    @Override
    public void unfreeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.FROZEN)) {
                unfreezeAllPairs();

                if (acceptor.isFrozen()) {
                    acceptor.unfreeze();
                }

                state.set(State.OPEN);

                return true;
            } else {
                throw new IllegalStateException("TcpCrusher is not frozen on unfreeze");
            }
        });
    }

    /**
     * Unfreezes all TCP pairs
     */
    public void unfreezeAllPairs() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                for (TcpPair pair : pairs.values()) {
                    if (pair.isFrozen()) {
                        pair.unfreeze();
                    }
                }

                return true;
            } else {
                throw new IllegalStateException("TcpCrusher is closed");
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
                return this.pairs.values().stream()
                    .map(TcpPair::getClientAddress)
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
                TcpPair pair = this.pairs.get(clientAddress);
                if (pair != null) {
                    return pair.getByteMeters();
                }
            }

            return null;
        });
    }

    @Override
    public boolean closeClient(InetSocketAddress clientAddress) {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                TcpPair pair = pairs.remove(clientAddress);
                if (pair != null) {
                    pair.close();
                    notifyPairDeleted(pair);
                    return true;
                }
            }

            return false;
        });
    }

    /**
     * Request freezer for the specific client
     * @param clientAddress Client address
     * @return Freezer or null if client address is not registered
     */
    public NetFreezer getClientFreezer(InetSocketAddress clientAddress) {
        return pairs.get(clientAddress);
    }

    /**
     * Return acceptor freezer
     * @return Freezer for acceptor or null
     */
    public NetFreezer getAcceptorFreezer() {
        return reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                return acceptor;
            } else {
                return null;
            }
        });
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

