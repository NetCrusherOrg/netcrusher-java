package org.netcrusher.datagram.main;

import org.netcrusher.core.filter.LoggingFilter;
import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.rate.ByteRateThrottler;
import org.netcrusher.core.throttle.rate.PacketRateThrottler;
import org.netcrusher.datagram.DatagramCrusher;
import org.netcrusher.datagram.DatagramCrusherBuilder;
import org.netcrusher.datagram.DatagramCrusherOptions;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class DatagramCrusherMain extends AbstractCrusherMain<DatagramCrusher> {

    private static final int DEFAULT_IDLE_PERIOD_SEC = 60;

    private static final String CMD_CLOSE_IDLE = "CLOSE-IDLE";

    @Override
    protected DatagramCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress)
    {
        DatagramCrusherBuilder builder = DatagramCrusherBuilder.builder();

        builder
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress);

        builder
            .withCreationListener((address) ->
                LOGGER.info("Client for <{}> is created", address));

        builder
            .withDeletionListener((address, byteMeters, packetMeters) -> {
                LOGGER.info("Client for <{}> is deleted", address);
                statusClientMeters(byteMeters, packetMeters);
            });

        builder
            .withBufferCount(Integer.getInteger("crusher.buffer.count",
                DatagramCrusherOptions.DEFAULT_BUFFER_COUNT))
            .withBufferSize(Integer.getInteger("crusher.buffer.size",
                DatagramCrusherOptions.DEFAULT_BUFFER_SIZE));

        builder
            .withRcvBufferSize(Integer.getInteger("crusher.socket.rcvbuf.size", 0))
            .withSndBufferSize(Integer.getInteger("crusher.socket.sndbuf.size", 0));

        final String loggerName = System.getProperty("crusher.logger", null);
        if (loggerName != null) {
            builder.withOutgoingTransformFilterFactory((addr) ->
                new LoggingFilter(addr, loggerName + ".outgoing", LoggingFilter.Level.INFO));
            builder.withIncomingTransformFilterFactory((addr) ->
                new LoggingFilter(addr, loggerName + ".incoming", LoggingFilter.Level.INFO));
        }

        final int packetRatePerSec = Integer.getInteger("crusher.throttler.packets", 0);
        if (packetRatePerSec > 0) {
            builder.withOutgoingThrottlerFactory((addr) ->
                new PacketRateThrottler(packetRatePerSec, 1, TimeUnit.SECONDS));
        }

        final int byteRatePerSec = Integer.getInteger("crusher.throttler.bytes", 0);
        if (byteRatePerSec > 0) {
            builder.withOutgoingThrottlerFactory((addr) ->
                new ByteRateThrottler(byteRatePerSec, 1, TimeUnit.SECONDS));
        }

        return builder.buildAndOpen();
    }

    @Override
    protected void printHelp() {
        super.printHelp();

        LOGGER.info("Commands for Datagram clients:");
        LOGGER.info("\t" + CMD_CLOSE_IDLE + " - close idle (> 60 sec) clients");
    }

    @Override
    protected void command(DatagramCrusher crusher, String command) {
        if (command.startsWith(CMD_CLOSE_IDLE)) {
            closeIdle(crusher);
        } else {
            super.command(crusher, command);
        }
    }

    private void closeIdle(DatagramCrusher crusher) {
        if (crusher.isOpen()) {
            int closed = crusher.closeIdleClients(DEFAULT_IDLE_PERIOD_SEC, TimeUnit.SECONDS);
            LOGGER.info("Idle clients are closed: {}", closed);
        } else {
            LOGGER.warn("Crusher is not open");
        }
    }

    @Override
    protected void status(DatagramCrusher crusher) {
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
    protected void statusClient(DatagramCrusher crusher, InetSocketAddress address) {
        RateMeters byteMeters = crusher.getClientByteMeters(address);
        RateMeters packetMeters = crusher.getClientPacketMeters(address);
        if (byteMeters != null && packetMeters != null) {
            LOGGER.info("Client statistics for <{}>", address);
            statusClientMeters(byteMeters, packetMeters);
        } else {
            LOGGER.warn("Client for <{}> is not found", address);
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
