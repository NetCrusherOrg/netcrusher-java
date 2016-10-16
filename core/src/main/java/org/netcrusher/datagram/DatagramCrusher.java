package org.netcrusher.datagram;

import org.netcrusher.NetCrusher;
import org.netcrusher.core.NioReactor;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;

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
 * // do some test on localhost:10081
 * crusher.crush();
 * // do other test on localhost:10081
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

    private final long maxIdleDurationMs;

    private final DatagramFilters filters;

    private DatagramInner inner;

    private volatile boolean open;

    public DatagramCrusher(
            NioReactor reactor,
            InetSocketAddress bindAddress,
            InetSocketAddress connectAddress,
            DatagramCrusherSocketOptions socketOptions,
            DatagramFilters filters,
            long maxIdleDurationMs)
    {
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.socketOptions = socketOptions;
        this.reactor = reactor;
        this.maxIdleDurationMs = maxIdleDurationMs;
        this.open = false;
        this.filters = filters;
    }

    @Override
    public synchronized void open() throws IOException {
        if (open) {
            throw new IllegalStateException("DatagramCrusher is already active");
        }

        this.inner = new DatagramInner(this, reactor, socketOptions, filters,
            bindAddress, connectAddress, maxIdleDurationMs);
        this.inner.unfreeze();

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
    public synchronized void crush() throws IOException {
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

    /**
     * Get outer socket controllers
     * @return Collection of outer socket controllers
     */
    public Collection<DatagramOuter> getOuters() {
        if (open) {
            return inner.getOuters();
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    /**
     * Get inner socket controller
     * @return Inner socket controller
     */
    public DatagramInner getInner() {
        if (open) {
            return inner;
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

}
