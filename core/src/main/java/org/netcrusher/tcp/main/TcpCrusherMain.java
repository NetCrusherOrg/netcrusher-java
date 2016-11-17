package org.netcrusher.tcp.main;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.filter.LoggingFilter;
import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.rate.ByteRateThrottler;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class TcpCrusherMain extends AbstractCrusherMain<TcpCrusher> {

    private static final String CMD_CLIENT_FREEZE = "CLIENT-FREEZE";
    private static final String CMD_CLIENT_UNFREEZE = "CLIENT-UNFREEZE";

    private static final String CMD_ACCEPTOR_FREEZE = "ACCEPTOR-FREEZE";
    private static final String CMD_ACCEPTOR_UNFREEZE = "ACCEPTOR-UNFREEZE";

    @Override
    protected TcpCrusher create(NioReactor reactor,
            InetSocketAddress bindAddress, InetSocketAddress connectAddress)
    {
        TcpCrusherBuilder builder = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress);

        builder.withCreationListener((address) ->
            LOGGER.info("Client for <{}> is created", address)
        );

        builder.withDeletionListener((address, byteMeters) -> {
            LOGGER.info("Client for <{}> is deleted", address);
            statusClientMeters(byteMeters);
        });

        withIntProperty("crusher.buffer.count", builder::withBufferCount);
        withIntProperty("crusher.buffer.size", builder::withBufferSize);

        withIntProperty("crusher.socket.backlog", builder::withBacklog);
        withLongProperty("crusher.socket.conn.timeout", builder::withConnectionTimeoutMs);
        withIntProperty("crusher.socket.rcvbuf.size", builder::withRcvBufferSize);
        withIntProperty("crusher.socket.sndbuf.size", builder::withSndBufferSize);
        withBoolProperty("crusher.socket.keepalive", builder::withKeepAlive);
        withIntProperty("crusher.socket.linger", builder::withLingerMs);

        withStrProperty("crusher.logger", (loggerName) -> {
            builder.withOutgoingTransformFilterFactory((addr) ->
                new LoggingFilter(addr, loggerName + ".outgoing", LoggingFilter.Level.INFO));
            builder.withIncomingTransformFilterFactory((addr) ->
                new LoggingFilter(addr, loggerName + ".incoming", LoggingFilter.Level.INFO));
        });

        withIntProperty("crusher.throttler.bytes", (bytePerSec) -> {
            builder.withOutgoingThrottlerFactory((addr) -> new ByteRateThrottler(bytePerSec, 1, TimeUnit.SECONDS));
            builder.withIncomingThrottlerFactory((addr) -> new ByteRateThrottler(bytePerSec, 1, TimeUnit.SECONDS));
        });

        return builder.buildAndOpen();
    }

    @Override
    protected void statusClient(TcpCrusher crusher, InetSocketAddress address) {
        RateMeters byteMeters = crusher.getClientByteMeters(address);
        if (byteMeters != null) {
            LOGGER.info("Client statistics for <{}>", address);
            statusClientMeters(byteMeters);
        } else {
            LOGGER.warn("Client for <{}> is not found", address);
        }
    }

    private void statusClientMeters(RateMeters meters) {
        LOGGER.info("\ttotal read bytes: {}", meters.getReadMeter().getTotal());
        LOGGER.info("\ttotal sent bytes: {}", meters.getSentMeter().getTotal());
    }

    @Override
    protected void printHelp() {
        super.printHelp();

        LOGGER.info("Commands for TCP clients:");
        LOGGER.info("\t" + CMD_CLIENT_FREEZE + " <addr>   - freezes the TCP client");
        LOGGER.info("\t" + CMD_CLIENT_UNFREEZE + " <addr> - unfreezes the TCP client");

        LOGGER.info("Commands for the TCP acceptor:");
        LOGGER.info("\t" + CMD_ACCEPTOR_FREEZE + "   - freezes the TCP acceptor");
        LOGGER.info("\t" + CMD_ACCEPTOR_UNFREEZE + " - unfreezes the TCP acceptor");
    }

    @Override
    protected void command(TcpCrusher crusher, String command) {
        if (command.startsWith(CMD_CLIENT_FREEZE)) {
            freezeClient(crusher, command);
        } else if (command.startsWith(CMD_CLIENT_UNFREEZE)) {
            unfreezeClient(crusher, command);
        } else if (command.equals(CMD_ACCEPTOR_FREEZE)) {
            freezeAcceptor(crusher);
        } else if (command.equals(CMD_ACCEPTOR_UNFREEZE)) {
            unfreezeAcceptor(crusher);
        } else {
            super.command(crusher, command);
        }
    }

    protected void freezeClient(TcpCrusher crusher, String command) {
        InetSocketAddress address = parseAddress(command);
        NetFreezer pair = crusher.getClientFreezer(address);
        if (pair != null) {
            pair.freeze();
            LOGGER.info("Pair for <{}> is frozen", address);
        } else {
            LOGGER.warn("Pair for <{}> is not found", address);
        }
    }

    protected void unfreezeClient(TcpCrusher crusher, String command) {
        InetSocketAddress address = parseAddress(command);
        NetFreezer pair = crusher.getClientFreezer(address);
        if (pair != null) {
            pair.unfreeze();
            LOGGER.info("Pair for <{}> is unfrozen", address);
        } else {
            LOGGER.warn("Pair for <{}> is not found", address);
        }
    }

    protected void freezeAcceptor(TcpCrusher crusher) {
        if (crusher.isOpen()) {
            if (!crusher.getAcceptorFreezer().isFrozen()) {
                crusher.getAcceptorFreezer().freeze();
                LOGGER.info("Acceptor is frozen");
            } else {
                LOGGER.warn("Acceptor is already frozen");
            }
        } else {
            LOGGER.warn("Crusher is not open");
        }
    }

    protected void unfreezeAcceptor(TcpCrusher crusher) {
        if (crusher.isOpen()) {
            if (crusher.getAcceptorFreezer().isFrozen()) {
                crusher.getAcceptorFreezer().unfreeze();
                LOGGER.info("Acceptor is unfrozen");
            } else {
                LOGGER.warn("Acceptor is already frozen");
            }
        } else {
            LOGGER.warn("Crusher is not open");
        }
    }

    public static void main(String[] arguments) throws Exception {
        TcpCrusherMain main = new TcpCrusherMain();
        main.run(arguments);
    }

}
