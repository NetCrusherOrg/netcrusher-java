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

import java.io.IOException;
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

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final TcpCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final Map<InetSocketAddress, TcpPair> pairs;

    private final TcpClientCreation creationListener;

    private final TcpClientDeletion deletionListener;

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
        BufferOptions bufferOptions)
    {
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.reactor = reactor;
        this.socketOptions = socketOptions;
        this.pairs = new ConcurrentHashMap<>(32);
        this.filters = filters;
        this.bufferOptions = bufferOptions;
        this.creationListener = creationListener;
        this.deletionListener = deletionListener;
        this.clientTotalCount = new AtomicInteger(0);
        this.state = new State(State.CLOSED);
    }

    void notifyPairCreated(TcpPair pair) {
        LOGGER.debug("Pair is created for <{}>", pair.getClientAddress());

        clientTotalCount.incrementAndGet();

        pairs.put(pair.getClientAddress(), pair);

        if (creationListener != null) {
            reactor.getScheduler().execute(() ->
                creationListener.created(pair.getClientAddress()));
        }
    }

    void notifyPairDeleted(TcpPair pair) {
        if (deletionListener != null) {
            reactor.getScheduler().execute(() ->
                deletionListener.deleted(pair.getClientAddress(), pair.getByteMeters()));
        }
    }

    @Override
    public void open() throws IOException {
        if (state.lockIf(State.CLOSED)) {
            try {
                this.acceptor = new TcpAcceptor(this, reactor, bindAddress, connectAddress, socketOptions,
                    filters, bufferOptions);

                clientTotalCount.set(0);

                state.set(State.FROZEN);

                LOGGER.info("TcpCrusher <{}>-<{}> is open", bindAddress, connectAddress);

                unfreeze();
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("TcpCrusher is already open");
        }
    }

    @Override
    public void close() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                closeAllPairs();

                acceptor.close();
                acceptor = null;

                state.set(State.CLOSED);

                LOGGER.info("TcpCrusher <{}>-<{}> is closed", bindAddress, connectAddress);
            } finally {
                state.unlock();
            }
        }
    }

    @Override
    public boolean isOpen() {
        return state.isAnyOf(State.OPEN | State.FROZEN);
    }

    /**
     * Close all pairs but keeps listening socket open
     */
    public void closeAllPairs() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                for (TcpPair pair : pairs.values()) {
                    pair.close();
                    notifyPairDeleted(pair);
                }

                pairs.clear();
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
            throw new IllegalStateException("TcpCrusher is already closed");
        }
    }

    /**
     * Freezes crusher proxy. Call freeze() on all pairs and freezes the acceptor
     * @see TcpCrusher#freezeAllPairs()
     * @see TcpPair#freeze()
     * @throws IOException On IO error
     */
    @Override
    public void freeze() throws IOException {
        if (state.lockIf(State.OPEN)) {
            try {
                if (!acceptor.isFrozen()) {
                    acceptor.freeze();
                }

                freezeAllPairs();

                state.set(State.FROZEN);
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("TcpCrusher is not in open state");
        }
    }

    /**
     * Freezes all TCP pairs
     * @throws IOException Throwed on IO error
     */
    public void freezeAllPairs() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                for (TcpPair pair : pairs.values()) {
                    if (!pair.isFrozen()) {
                        pair.freeze();
                    }
                }
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("TcpCrusher is closed");
        }
    }

    /**
     * Unfreezes the crusher. Call unfreeze() on all pairs and unfreezes the acceptor
     * @see TcpCrusher#unfreezeAllPairs()
     * @see TcpPair#unfreeze()
     * @throws IOException On IO error
     */
    @Override
    public void unfreeze() throws IOException {
        if (state.lockIf(State.FROZEN)) {
            try {
                unfreezeAllPairs();

                if (acceptor.isFrozen()) {
                    acceptor.unfreeze();
                }

                state.set(State.OPEN);
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("TcpCrusher is not frozen");
        }
    }

    /**
     * Unfreezes all TCP pairs
     * @throws IOException Throwed on IO error
     */
    public void unfreezeAllPairs() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                for (TcpPair pair : pairs.values()) {
                    if (pair.isFrozen()) {
                        pair.unfreeze();
                    }
                }
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("TcpCrusher is closed");
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
                return this.pairs.values().stream()
                    .map(TcpPair::getClientAddress)
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
                TcpPair pair = this.pairs.get(clientAddress);
                if (pair != null) {
                    return pair.getByteMeters();
                }
            } finally {
                state.unlock();
            }
        }

        return null;
    }

    @Override
    public boolean closeClient(InetSocketAddress clientAddress) throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                TcpPair pair = pairs.remove(clientAddress);
                if (pair != null) {
                    pair.close();
                    notifyPairDeleted(pair);
                    return true;
                }
            } finally {
                state.unlock();
            }
        }

        return false;
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
     * @return Freezer for acceptor
     */
    public NetFreezer getAcceptorFreezer() {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                return acceptor;
            } finally {
                state.unlock();
            }
        } else {
            throw new IllegalStateException("TcpCrusher is closed");
        }
    }

    @Override
    public int getClientTotalCount() {
        return clientTotalCount.get();
    }

    private static class State extends BitState {

        private static final int OPEN = bit(0);

        private static final int FROZEN = bit(1);

        private static final int CLOSED = bit(2);

        private State(int state) {
            super(state);
        }
    }
}

