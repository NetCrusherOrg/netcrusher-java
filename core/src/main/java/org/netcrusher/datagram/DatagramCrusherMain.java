package org.netcrusher.datagram;

import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DatagramCrusherMain extends AbstractCrusherMain<DatagramCrusher> {

    @Override
    protected DatagramCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .buildAndOpen();
    }

    @Override
    protected void status(DatagramCrusher crusher) throws IOException {
        LOGGER.info("Datagram crusher for <{}>-<{}>", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            LOGGER.info("Inner");

            RateMeters innerByteMeters = crusher.getInnerByteMeters();
            LOGGER.info("\ttotal read bytes: {}", innerByteMeters.getReadMeter().getTotal());
            LOGGER.info("\ttotal sent bytes: {}", innerByteMeters.getSentMeter().getTotal());

            RateMeters innerPacketMeters = crusher.getInnerPacketMeters();
            LOGGER.info("\ttotal read datagrams: {}", innerPacketMeters.getReadMeter().getTotal());
            LOGGER.info("\ttotal sent datagrams: {}", innerPacketMeters.getSentMeter().getTotal());

            for (InetSocketAddress clientAddress : crusher.getClientAddresses()) {
                LOGGER.info("Outer for <{}>", clientAddress);

                RateMeters outerByteMeters = crusher.getClientByteMeters(clientAddress);
                if (outerByteMeters != null) {
                    LOGGER.info("\ttotal read bytes: {}", outerByteMeters.getReadMeter().getTotal());
                    LOGGER.info("\ttotal sent bytes: {}", outerByteMeters.getSentMeter().getTotal());
                }

                RateMeters outerPacketMeters = crusher.getClientPacketMeters(clientAddress);
                if (outerPacketMeters != null) {
                    LOGGER.info("\ttotal read datagrams: {}", outerPacketMeters.getReadMeter().getTotal());
                    LOGGER.info("\ttotal sent datagrams: {}", outerPacketMeters.getSentMeter().getTotal());
                }
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        DatagramCrusherMain main = new DatagramCrusherMain();
        main.run(arguments);
    }
}
