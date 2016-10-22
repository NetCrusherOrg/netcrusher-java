package org.netcrusher.datagram;

import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class DatagramCrusherMain extends AbstractCrusherMain<DatagramCrusher> {

    private static final String CMD_CLOSE_IDLE = "CLOSE-IDLE";

    @Override
    protected DatagramCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .withCreationListener((addr) -> {
                LOGGER.info("Client for <{}> is created", addr);
            })
            .withDeletionListener((addr, byteMeters, packetMeters) -> {
                LOGGER.info("Client for <{}> is deleted", addr);
                statusClientMeters(byteMeters, packetMeters);
            })
            .buildAndOpen();
    }

    @Override
    protected void printHelp() {
        super.printHelp();

        LOGGER.info("Commands for Datagram clients:");
        LOGGER.info("\t" + CMD_CLOSE_IDLE + " - close idle (> 60 sec) clients");
    }

    @Override
    protected void command(DatagramCrusher crusher, String command) throws IOException {
        if (command.startsWith(CMD_CLOSE_IDLE)) {
            closeIdle(crusher, command);
        } else {
            super.command(crusher, command);
        }
    }

    protected void closeIdle(DatagramCrusher crusher, String command) throws IOException {
        crusher.closeIdleClients(60, TimeUnit.SECONDS);
        LOGGER.info("Idle clients are closed");
    }

    @Override
    protected void status(DatagramCrusher crusher) throws IOException {
        super.status(crusher);

        if (crusher.isOpen()) {
            LOGGER.info("Inner statistics");

            RateMeters innerByteMeters = crusher.getInnerByteMeters();
            LOGGER.info("\ttotal read bytes: {}", innerByteMeters.getReadMeter().getTotal());
            LOGGER.info("\ttotal sent bytes: {}", innerByteMeters.getSentMeter().getTotal());

            RateMeters innerPacketMeters = crusher.getInnerPacketMeters();
            LOGGER.info("\ttotal read packets: {}", innerPacketMeters.getReadMeter().getTotal());
            LOGGER.info("\ttotal sent packets: {}", innerPacketMeters.getSentMeter().getTotal());
        }
    }

    @Override
    protected void statusClient(DatagramCrusher crusher, InetSocketAddress addr) throws IOException {
        RateMeters byteMeters = crusher.getClientByteMeters(addr);
        RateMeters packetMeters = crusher.getClientPacketMeters(addr);
        if (byteMeters != null && packetMeters != null) {
            LOGGER.info("Client statistics for <{}>", addr);
            statusClientMeters(byteMeters, packetMeters);
        } else {
            LOGGER.info("Client for <{}> is not found", addr);
        }
    }

    private void statusClientMeters(RateMeters byteMeters, RateMeters packetMeters) {
        LOGGER.info("\ttotal read bytes: {}", byteMeters.getReadMeter().getTotal());
        LOGGER.info("\ttotal sent bytes: {}", byteMeters.getSentMeter().getTotal());

        LOGGER.info("\ttotal read packets: {}", packetMeters.getReadMeter().getTotal());
        LOGGER.info("\ttotal sent packets: {}", packetMeters.getSentMeter().getTotal());
    }

    public static void main(String[] arguments) throws Exception {
        DatagramCrusherMain main = new DatagramCrusherMain();
        main.run(arguments);
    }
}
