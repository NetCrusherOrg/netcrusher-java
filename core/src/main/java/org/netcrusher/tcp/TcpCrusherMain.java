package org.netcrusher.tcp;

import org.netcrusher.core.NioUtils;
import org.netcrusher.core.main.AbstractCrusherMain;
import org.netcrusher.core.reactor.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TcpCrusherMain extends AbstractCrusherMain<TcpCrusher> {

    private static final String CMD_PAIR_FREEZE = "PAIR-FREEZE";

    private static final String CMD_PAIR_UNFREEZE = "PAIR-UNFREEZE";

    private static final String CMD_PAIR_CLOSE = "PAIR-CLOSE";

    private static final String CMD_PAIR_STATUS = "PAIR-STATUS";

    @Override
    protected TcpCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .withDeletionListener((pair) -> {
                LOGGER.info("Pair for <{}> is deleted", pair.getClientAddress());
                reportPair(pair);
            })
            .withCreationListener((pair -> {
                LOGGER.info("Pair for <{}> is created", pair.getClientAddress());
            }))
            .buildAndOpen();
    }

    @Override
    protected void status(TcpCrusher crusher) throws IOException {
        LOGGER.info("TCP crusher for <{}>-<{}>", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            LOGGER.info("Total accepted: {}", crusher.getAcceptedCount());

            for (TcpPair pair : crusher.getPairs()) {
                reportPair(pair);
            }
        }
    }

    private void reportPair(TcpPair pair) {
        LOGGER.info("Pair statistics for <{}>", pair.getClientAddress());

        TcpTransfer inner = pair.getInnerTransfer();
        LOGGER.info("\tinner total read bytes: {}", inner.getReadMeter().getTotal());
        LOGGER.info("\tinner total sent bytes: {}", inner.getSentMeter().getTotal());

        TcpTransfer outer = pair.getOuterTransfer();
        LOGGER.info("\touter total read bytes: {}", outer.getReadMeter().getTotal());
        LOGGER.info("\touter total sent bytes: {}", outer.getSentMeter().getTotal());
    }

    @Override
    protected void printHelp() {
        super.printHelp();

        LOGGER.info("Commands for TCP pairs:");
        LOGGER.info("\t" + CMD_PAIR_FREEZE + " <addr>   - freezes the TCP pair");
        LOGGER.info("\t" + CMD_PAIR_UNFREEZE + " <addr> - unfreezes the TCP pair");
        LOGGER.info("\t" + CMD_PAIR_CLOSE + " <addr>    - closes the TCP pair");
        LOGGER.info("\t" + CMD_PAIR_STATUS + " <addr>   - prints status of the TCP pair");
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
        } else {
            super.command(crusher, command);
        }
    }

    protected void freezePair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        TcpPair pair = crusher.getPair(addr);
        if (pair != null) {
            pair.freeze();
            LOGGER.info("Pair for <{}> is frozen", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void unfreezePair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        TcpPair pair = crusher.getPair(addr);
        if (pair != null) {
            pair.unfreeze();
            LOGGER.info("Pair for <{}> is unfrozen", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void closePair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        boolean closed = crusher.closePair(addr);
        if (closed) {
            LOGGER.info("Pair for <{}> is closed", addr);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
        }
    }

    protected void statusPair(TcpCrusher crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        TcpPair pair = crusher.getPair(addr);
        if (pair != null) {
            reportPair(pair);
        } else {
            LOGGER.info("Pair for <{}> is not found", addr);
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
