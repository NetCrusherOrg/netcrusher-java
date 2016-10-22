package org.netcrusher.core.main;

import org.netcrusher.NetCrusher;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.reactor.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

public abstract class AbstractCrusherMain<T extends NetCrusher> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrusherMain.class);

    private static final String CMD_OPEN = "OPEN";
    private static final String CMD_CLOSE = "CLOSE";
    private static final String CMD_REOPEN = "REOPEN";
    private static final String CMD_FREEZE = "FREEZE";
    private static final String CMD_UNFREEZE = "UNFREEZE";
    private static final String CMD_STATUS = "STATUS";
    private static final String CMD_HELP = "HELP";
    private static final String CMD_QUIT = "QUIT";

    private static final String CMD_CLIENT_CLOSE = "CLIENT-CLOSE";
    private static final String CMD_CLIENT_STATUS = "CLIENT-STATUS";

    protected int run(String[] arguments) {
        if (arguments == null || arguments.length != 2) {
            printUsage();
            return 1;
        }

        InetSocketAddress bindAddress;
        try {
            bindAddress = NioUtils.parseInetSocketAddress(arguments[0]);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Fail to parse address: {}", arguments[0]);
            return 2;
        }

        InetSocketAddress connectAddress;
        try {
            connectAddress = NioUtils.parseInetSocketAddress(arguments[1]);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Fail to parse address: {}", arguments[1]);
            return 2;
        }

        NioReactor reactor;
        try {
            reactor = new NioReactor();
        } catch (Exception e) {
            LOGGER.error("Fail to create reactor", e);
            return 3;
        }

        T crusher;
        try {
            crusher = create(reactor, bindAddress, connectAddress);
        } catch (Exception e) {
            LOGGER.error("Fail to create crusher", e);
            reactor.close();
            return 3;
        }

        String version = getClass().getPackage().getImplementationVersion();
        if (version != null && !version.isEmpty()) {
            System.out.printf("# Version: %s\n", version);
        }

        System.out.println("# Print `HELP` for the list of the commands");

        repl(crusher);

        try {
            close(crusher);
        } catch (Exception e) {
            LOGGER.error("Fail to close the crusher", e);
        }

        try {
            reactor.close();
        } catch (Exception e) {
            LOGGER.error("Fail to close the reactor", e);
        }

        LOGGER.info("Exiting..");

        return 0;
    }

    protected void repl(T crusher) {
        try {
            try (InputStreamReader isr = new InputStreamReader(System.in);
                 BufferedReader br = new BufferedReader(isr))
            {
                while (true) {
                    System.out.println("# enter the command in the next line");

                    String line = br.readLine();
                    if (line == null) {
                        break;
                    }

                    if (line.isEmpty()) {
                        LOGGER.warn("Command is empty");
                    } else if ("QUIT".equals(line)) {
                        break;
                    } else {
                        try {
                            command(crusher, line);
                        } catch (Throwable e) {
                            LOGGER.error("Command failed: '{}'", line, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("REPL error", e);
        }
    }

    protected void command(T crusher, String command) throws IOException {
        if (command.equals(CMD_OPEN)) {
            open(crusher);
        } else if (command.equals(CMD_CLOSE)) {
            close(crusher);
        } else if (command.equals(CMD_REOPEN)) {
            reopen(crusher);
        } else if (command.equals(CMD_STATUS)) {
            status(crusher);
        } else if (command.equals(CMD_FREEZE)) {
            freeze(crusher);
        } else if (command.equals(CMD_UNFREEZE)) {
            unfreeze(crusher);
        } else if (command.equals(CMD_HELP)) {
            printHelp();
        } else if (command.startsWith(CMD_CLIENT_CLOSE)) {
            closeClient(crusher, command);
        } else if (command.startsWith(CMD_CLIENT_STATUS)) {
            statusClient(crusher, command);
        } else {
            LOGGER.warn("Command is unknown: '{}'", command);
        }
    }

    protected void open(T crusher) throws IOException {
        if (crusher.isOpen()) {
            LOGGER.info("Crusher is already open");
        } else {
            crusher.open();
            LOGGER.info("Crusher is open");
        }
    }

    protected void close(T crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.close();
            LOGGER.info("Crusher is closed");
        } else {
            LOGGER.info("Crusher is already closed");
        }
    }

    protected void reopen(T crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.reopen();
            LOGGER.info("Crusher is reopen");
        } else {
            LOGGER.info("Crusher is closed");
        }
    }

    protected void freeze(T crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.freeze();
            LOGGER.info("Crusher is freezed");
        } else {
            LOGGER.info("Crusher is closed");
        }
    }

    protected void unfreeze(T crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.unfreeze();
            LOGGER.info("Crusher is freezed");
        } else {
            LOGGER.info("Crusher is closed");
        }
    }

    protected void status(T crusher) throws IOException {
        LOGGER.info("{} crusher for <{}>-<{}>", new Object[] {
            crusher.getClass().getSimpleName(), crusher.getBindAddress(), crusher.getConnectAddress()
        });

        if (crusher.isOpen()) {
            LOGGER.info("Crusher is open");

            if (crusher.isFrozen()) {
                LOGGER.info("Crusher is frozen");
            }

            LOGGER.info("Total number of registered clients: {}", crusher.getClientTotalCount());
            LOGGER.info("Total number of active clients: {}", crusher.getClientAddresses().size());

            for (InetSocketAddress clientAddress : crusher.getClientAddresses()) {
                statusClient(crusher, clientAddress);
            }
        } else {
            LOGGER.info("Crusher is closed");
        }
    }

    protected void closeClient(T crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        boolean closed = crusher.closeClient(addr);
        if (closed) {
            LOGGER.info("Client for <{}> is closed", addr);
        } else {
            LOGGER.info("Client for <{}> is not found", addr);
        }
    }

    protected void statusClient(T crusher, String command) throws IOException {
        InetSocketAddress addr = parseAddress(command);
        statusClient(crusher, addr);
    }

    protected void statusClient(T crusher, InetSocketAddress addr) throws IOException {
        LOGGER.info("Client address <{}>", addr);
    }

    protected void printUsage() {
        LOGGER.info("Execution: {} <bind-socket-address:port> <connect-socket-address:port>",
            this.getClass().getSimpleName());
    }

    protected void printHelp() {
        LOGGER.info("Commands:");
        LOGGER.info("\t" + CMD_OPEN + "     - opens the crusher");
        LOGGER.info("\t" + CMD_CLOSE + "    - closes the crusher (sockets will be closed)");
        LOGGER.info("\t" + CMD_REOPEN + "   - closes and opens the crusher again");
        LOGGER.info("\t" + CMD_FREEZE + "   - freezes the crusher (open but transfer engine is not working)");
        LOGGER.info("\t" + CMD_UNFREEZE + " - unfreezes the crusher");
        LOGGER.info("\t" + CMD_STATUS + "   - prints the status of the connection");
        LOGGER.info("\t" + CMD_HELP + "     - prints this help");
        LOGGER.info("\t" + CMD_QUIT + "     - quits the program");

        LOGGER.info("Commands for clients:");
        LOGGER.info("\t" + CMD_CLIENT_CLOSE + " <addr>    - closes the client");
        LOGGER.info("\t" + CMD_CLIENT_STATUS + " <addr>   - prints status of the client");
    }

    protected static InetSocketAddress parseAddress(String command) {
        String[] items = command.split(" ", 2);
        if (items.length == 2) {
            return NioUtils.parseInetSocketAddress(items[1]);
        } else {
            throw new IllegalArgumentException("Fail to parse address from command: " + command);
        }
    }

    protected abstract T create(NioReactor reactor,
        InetSocketAddress bindAddress, InetSocketAddress connectAddress) throws IOException;

}

