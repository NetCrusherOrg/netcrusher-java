package org.netcrusher.tcp;

import org.netcrusher.NetCrusher;
import org.netcrusher.NetFreezer;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
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

    private final int bufferCount;

    private final int bufferSize;

    private final TcpFilters filters;

    private final AtomicInteger clientTotalCount;

    private TcpAcceptor acceptor;

    private volatile boolean open;

    private volatile boolean frozen;

    public TcpCrusher(
            NioReactor reactor,
            InetSocketAddress bindAddress,
            InetSocketAddress connectAddress,
            TcpCrusherSocketOptions socketOptions,
            TcpClientCreation creationListener,
            TcpClientDeletion deletionListener,
            TcpFilters filters,
            int bufferCount,
            int bufferSize)
    {
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.reactor = reactor;
        this.socketOptions = socketOptions;
        this.pairs = new ConcurrentHashMap<>(32);
        this.filters = filters;
        this.bufferCount = bufferCount;
        this.bufferSize = bufferSize;
        this.creationListener = creationListener;
        this.deletionListener = deletionListener;
        this.clientTotalCount = new AtomicInteger(0);
        this.open = false;
        this.frozen = true;
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
    public synchronized void open() throws IOException {
        if (open) {
            throw new IllegalStateException("TcpCrusher is already active");
        }

        this.acceptor = new TcpAcceptor(this, reactor, bindAddress, connectAddress, socketOptions,
            filters, bufferCount, bufferSize);

        clientTotalCount.set(0);

        frozen = true;
        open = true;

        LOGGER.info("TcpCrusher <{}>-<{}> is open", bindAddress, connectAddress);

        unfreeze();
    }

    @Override
    public synchronized void close() throws IOException {
        if (open) {
            freeze();

            closeAllPairs();

            acceptor.closeExternal();
            acceptor = null;

            LOGGER.info("TcpCrusher <{}>-<{}> is closed", bindAddress, connectAddress);

            open = false;
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Close all pairs but keeps listening socket open
     */
    public synchronized void closeAllPairs() throws IOException {
        if (open) {
            for (TcpPair pair : pairs.values()) {
                pair.closeExternal();
                notifyPairDeleted(pair);
            }
            pairs.clear();
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    @Override
    public synchronized void reopen() throws IOException {
        if (open) {
            close();
            open();
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    /**
     * Freezes crusher proxy. Call freeze() on all pairs and freezes the acceptor
     * @see TcpCrusher#freezeAllPairs()
     * @see TcpPair#freeze()
     * @throws IOException On IO error
     */
    @Override
    public synchronized void freeze() throws IOException {
        if (open) {
            if (!frozen) {
                acceptor.freeze();
                freezeAllPairs();
                frozen = true;
            }
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    /**
     * Freezes all TCP pairs
     * @throws IOException Throwed on IO error
     */
    public synchronized void freezeAllPairs() throws IOException {
        if (open) {
            for (TcpPair pair : pairs.values()) {
                pair.freeze();
            }
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    /**
     * Unfreezes the crusher. Call unfreeze() on all pairs and unfreezes the acceptor
     * @see TcpCrusher#unfreezeAllPairs()
     * @see TcpPair#unfreeze()
     * @throws IOException On IO error
     */
    @Override
    public synchronized void unfreeze() throws IOException {
        if (open) {
            if (frozen) {
                unfreezeAllPairs();
                acceptor.unfreeze();
                frozen = false;
            }
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    /**
     * Unfreezes all TCP pairs
     * @throws IOException Throwed on IO error
     */
    public synchronized void unfreezeAllPairs() throws IOException {
        if (open) {
            for (TcpPair pair : pairs.values()) {
                pair.unfreeze();
            }
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    @Override
    public synchronized boolean isFrozen() {
        if (open) {
            return frozen;
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
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
        if (open) {
            return this.pairs.values().stream()
                .map(TcpPair::getClientAddress)
                .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public RateMeters getClientByteMeters(InetSocketAddress clientAddress) {
        if (open) {
            TcpPair pair = this.pairs.get(clientAddress);
            if (pair != null) {
                return pair.getByteMeters();
            }
        }

        return null;
    }

    @Override
    public boolean closeClient(InetSocketAddress clientAddress) throws IOException {
        if (open) {
            TcpPair pair = pairs.remove(clientAddress);
            if (pair != null) {
                pair.closeExternal();
                notifyPairDeleted(pair);
                return true;
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
        if (open) {
            return acceptor;
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    @Override
    public int getClientTotalCount() {
        return clientTotalCount.get();
    }
}

