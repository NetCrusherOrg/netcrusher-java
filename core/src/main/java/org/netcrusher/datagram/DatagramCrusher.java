package org.netcrusher.datagram;

import org.netcrusher.NetCrusher;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.datagram.callback.DatagramClientCreation;
import org.netcrusher.datagram.callback.DatagramClientDeletion;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
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

    private final int queueLimit;

    private final DatagramFilters filters;

    private final AtomicInteger clientTotalCount;

    private final DatagramClientCreation creationListener;

    private final DatagramClientDeletion deletionListener;

    private DatagramInner inner;

    private volatile boolean open;

    public DatagramCrusher(
            NioReactor reactor,
            InetSocketAddress bindAddress,
            InetSocketAddress connectAddress,
            DatagramCrusherSocketOptions socketOptions,
            DatagramFilters filters,
            DatagramClientCreation creationListener,
            DatagramClientDeletion deletionListener,
            int queueLimit)
    {
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.socketOptions = socketOptions;
        this.reactor = reactor;
        this.open = false;
        this.filters = filters;
        this.queueLimit = queueLimit;
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
    public synchronized void open() throws IOException {
        if (open) {
            throw new IllegalStateException("DatagramCrusher is already active");
        }

        this.inner = new DatagramInner(this, reactor, socketOptions, filters,
            bindAddress, connectAddress, queueLimit);
        this.inner.unfreeze();

        clientTotalCount.set(0);

        LOGGER.info("DatagramCrusher <{}>-<{}> is started", bindAddress, connectAddress);

        this.open = true;
    }

    @Override
    public synchronized void close() throws IOException {
        if (open) {
            this.inner.closeExternal();
            this.inner = null;

            this.open = false;

            LOGGER.info("DatagramCrusher <{}>-<{}> is closed", bindAddress, connectAddress);
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

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void freeze() throws IOException {
        if (open) {
            inner.freeze();
        } else {
            LOGGER.debug("Component is closed on freeze");
        }
    }

    @Override
    public synchronized void unfreeze() throws IOException {
        if (open) {
            inner.unfreeze();
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    @Override
    public synchronized boolean isFrozen() {
        if (open) {
            return inner.isFrozen();
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
    public synchronized Collection<InetSocketAddress> getClientAddresses() {
        if (open) {
            return inner.getOuters().stream()
                .map(DatagramOuter::getClientAddress)
                .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized RateMeters getClientByteMeters(InetSocketAddress clientAddress) {
        if (open) {
            DatagramOuter outer = this.inner.getOuter(clientAddress);
            if (outer != null) {
                return outer.getByteMeters();
            }
        }

        return null;
    }

    /**
     * Get client packet meters
     * @return Rate meters
     */
    public synchronized RateMeters getClientPacketMeters(InetSocketAddress clientAddress) {
        if (open) {
            DatagramOuter outer = this.inner.getOuter(clientAddress);
            if (outer != null) {
                return outer.getPacketMeters();
            }
        }

        return null;
    }

    /**
     * Get inner socket byte meters
     * @return Rate meters
     */
    public synchronized RateMeters getInnerByteMeters() {
        if (open) {
            return new RateMeters(inner.getReadByteMeter(), inner.getSentByteMeter());
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    /**
     * Get inner socket packet meters
     * @return Rate meters
     */
    public synchronized RateMeters getInnerPacketMeters() {
        if (open) {
            return new RateMeters(inner.getReadPacketMeter(), inner.getSentPacketMeter());
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    @Override
    public synchronized boolean closeClient(InetSocketAddress clientAddress) throws IOException {
        if (open) {
            return this.inner.closeOuter(clientAddress);
        }

        return false;
    }

    /**
     * Close idle clients
     * @param maxIdleDurationMs Maximum allowed idle time (in milliseconds)
     * @throws IOException Exception on error
     */
    public synchronized void closeIdleClients(long maxIdleDurationMs) throws IOException {
        if (open) {
            this.inner.closeIdleOuters(maxIdleDurationMs);
        }
    }

    @Override
    public int getClientTotalCount() {
        return clientTotalCount.get();
    }

}
