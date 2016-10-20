package org.netcrusher.datagram;

import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.reactor.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DatagramCrusherMain extends AbstractCrusherMain<DatagramCrusher> {

    private static final long MAX_IDLE_DURATION_MS = 60000;

    @Override
    protected DatagramCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .withMaxIdleDurationMs(MAX_IDLE_DURATION_MS)
            .buildAndOpen();
    }

    @Override
    protected void status(DatagramCrusher crusher) throws IOException {
        LOGGER.info("Datagram crusher for <{}>-<{}>", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            LOGGER.info("Inner");

            DatagramInner inner = crusher.getInner();
            LOGGER.info("\ttotal read bytes: {}", inner.getReadByteMeter().getTotal());
            LOGGER.info("\ttotal read datagrams: {}", inner.getReadDatagramMeter().getTotal());
            LOGGER.info("\ttotal sent bytes: {}", inner.getSentByteMeter().getTotal());
            LOGGER.info("\ttotal sent datagrams: {}", inner.getSentDatagramMeter().getTotal());

            for (DatagramOuter outer : crusher.getOuters()) {
                LOGGER.info("Outer for {}", outer.getClientAddress());
                LOGGER.info("\ttotal read bytes: {}", outer.getReadByteMeter().getTotal());
                LOGGER.info("\ttotal read datagrams: {}", outer.getReadDatagramMeter().getTotal());
                LOGGER.info("\ttotal sent bytes: {}", outer.getSentByteMeter());
                LOGGER.info("\ttotal sent datagrams: {}", outer.getSentDatagramMeter().getTotal());
                LOGGER.info("\tidle duration, ms: {}", outer.getIdleDurationMs());
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        DatagramCrusherMain main = new DatagramCrusherMain();
        main.run(arguments);
    }
}
