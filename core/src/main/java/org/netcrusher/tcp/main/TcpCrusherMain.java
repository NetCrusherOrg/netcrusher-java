package org.netcrusher.tcp.main;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TcpCrusherMain extends AbstractCrusherMain<TcpCrusher> {

    private static final String CMD_CLIENT_FREEZE = "CLIENT-FREEZE";
    private static final String CMD_CLIENT_UNFREEZE = "CLIENT-UNFREEZE";

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
            .withDeletionListener((addr, byteMeters) -> {
                LOGGER.info("Client for <{}> is deleted", addr);
                statusClientMeters(byteMeters);
            })
            .buildAndOpen();
    }

    @Override
    protected void statusClient(TcpCrusher crusher, InetSocketAddress addr) throws IOException {
        RateMeters byteMeters = crusher.getClientByteMeters(addr);
        if (byteMeters != null) {
            LOGGER.info("Client statistics for <{}>", addr);
            statusClientMeters(byteMeters);
        } else {
            LOGGER.info("Client for <{}> is not found", addr);
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
    protected void command(TcpCrusher crusher, String command) throws IOException {
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

    protected void freezeClient(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        NetFreezer pair = crusher.getClientFreezer(addr);
        if (pair != null) {
            pair.freeze();
            LOGGER.info("Pair for <{}> is frozen", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void unfreezeClient(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        NetFreezer pair = crusher.getClientFreezer(addr);
        if (pair != null) {
            pair.unfreeze();
            LOGGER.info("Pair for <{}> is unfrozen", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void freezeAcceptor(TcpCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.getAcceptorFreezer().freeze();
            LOGGER.info("Acceptor is frozen");
        } else {
            LOGGER.info("Crusher is already closed");
        }
    }

    protected void unfreezeAcceptor(TcpCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.getAcceptorFreezer().unfreeze();
            LOGGER.info("Acceptor is unfrozen");
        } else {
            LOGGER.info("Crusher is already closed");
        }
    }

    public static void main(String[] arguments) throws Exception {
        TcpCrusherMain main = new TcpCrusherMain();
        main.run(arguments);
    }

}
