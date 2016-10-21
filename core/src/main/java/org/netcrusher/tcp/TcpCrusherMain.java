package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TcpCrusherMain extends AbstractCrusherMain<TcpCrusher> {

    private static final String CMD_PAIR_FREEZE = "PAIR-FREEZE";
    private static final String CMD_PAIR_UNFREEZE = "PAIR-UNFREEZE";
    private static final String CMD_PAIR_CLOSE = "PAIR-CLOSE";
    private static final String CMD_PAIR_STATUS = "PAIR-STATUS";

    private static final String CMD_ACCEPTOR_FREEZE = "ACCEPTOR-FREEZE";
    private static final String CMD_ACCEPTOR_UNFREEZE = "ACCEPTOR-UNFREEZE";

    @Override
    protected TcpCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .withCreationListener((addr) -> {
                LOGGER.info("Client for <{}> is created", addr);
            })
            .withDeletionListener((addr, meters) -> {
                LOGGER.info("Client for <{}> is deleted", addr);
                reportClientMeters(meters);
            })
            .buildAndOpen();
    }

    @Override
    protected void status(TcpCrusher crusher) throws IOException {
        LOGGER.info("TCP crusher for <{}>-<{}>", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            crusher.getClientAddresses().forEach((addr) -> this.reportClient(crusher, addr));
        }
    }

    private void reportClient(TcpCrusher crusher, InetSocketAddress clientAddress) {
        LOGGER.info("Pair statistics for <{}>", clientAddress);

        RateMeters meters = crusher.getClientByteMeters(clientAddress);
        if (meters != null) {
            reportClientMeters(meters);
        }
    }

    private void reportClientMeters(RateMeters meters) {
        LOGGER.info("\ttotal read bytes: {}", meters.getReadMeter().getTotal());
        LOGGER.info("\ttotal sent bytes: {}", meters.getSentMeter().getTotal());
    }

    @Override
    protected void printHelp() {
        super.printHelp();

        LOGGER.info("Commands for TCP pairs:");
        LOGGER.info("\t" + CMD_PAIR_FREEZE + " <addr>   - freezes the TCP pair");
        LOGGER.info("\t" + CMD_PAIR_UNFREEZE + " <addr> - unfreezes the TCP pair");
        LOGGER.info("\t" + CMD_PAIR_CLOSE + " <addr>    - closes the TCP pair");
        LOGGER.info("\t" + CMD_PAIR_STATUS + " <addr>   - prints status of the TCP pair");

        LOGGER.info("Commands for the acceptor:");
        LOGGER.info("\t" + CMD_ACCEPTOR_FREEZE + "   - freezes the acceptor");
        LOGGER.info("\t" + CMD_ACCEPTOR_UNFREEZE + " - unfreezes the acceptor");
    }

    @Override
    protected void command(TcpCrusher crusher, String command) throws IOException {
        if (command.startsWith(CMD_PAIR_FREEZE)) {
            freezePair(crusher, command);
        } else if (command.startsWith(CMD_PAIR_UNFREEZE)) {
            unfreezePair(crusher, command);
        } else if (command.startsWith(CMD_PAIR_CLOSE)) {
            closePair(crusher, command);
        } else if (command.startsWith(CMD_PAIR_STATUS)) {
            statusPair(crusher, command);
        } else if (command.equals(CMD_ACCEPTOR_FREEZE)) {
            freezeAcceptor(crusher);
        } else if (command.equals(CMD_ACCEPTOR_UNFREEZE)) {
            unfreezeAcceptor(crusher);
        } else {
            super.command(crusher, command);
        }
    }

    protected void freezePair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        NetFreezer pair = crusher.getClientFreezer(addr);
        if (pair != null) {
            pair.freeze();
            LOGGER.info("Pair for <{}> is frozen", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void unfreezePair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        NetFreezer pair = crusher.getClientFreezer(addr);
        if (pair != null) {
            pair.unfreeze();
            LOGGER.info("Pair for <{}> is unfrozen", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void closePair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        boolean closed = crusher.closeClient(addr);
        if (closed) {
            LOGGER.info("Pair for <{}> is closed", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void statusPair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        reportClient(crusher, addr);
    }

    protected void freezeAcceptor(TcpCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.freezeAcceptor();
            LOGGER.info("Acceptor is frozen");
        } else {
            LOGGER.info("Crusher is already closed");
        }
    }

    protected void unfreezeAcceptor(TcpCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.unfreezeAcceptor();
            LOGGER.info("Acceptor is unfrozen");
        } else {
            LOGGER.info("Crusher is already closed");
        }
    }

    private InetSocketAddress parseAddress(String command) {
        String[] items = command.split(" ", 2);
        if (items.length == 2) {
            return NioUtils.parseInetSocketAddress(items[1]);
        } else {
            throw new IllegalArgumentException("Fail to parse address from command: " + command);
        }
    }

    public static void main(String[] arguments) throws Exception {
        TcpCrusherMain main = new TcpCrusherMain();
        main.run(arguments);
    }

}
