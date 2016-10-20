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

    protected static final String CMD_OPEN = "OPEN";
    protected static final String CMD_CLOSE = "CLOSE";
    protected static final String CMD_FREEZE = "FREEZE";
    protected static final String CMD_UNFREEZE = "UNFREEZE";
    protected static final String CMD_STATUS = "STATUS";
    protected static final String CMD_HELP = "HELP";
    protected static final String CMD_QUIT = "QUIT";

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

                    if ("QUIT".equals(line)) {
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
        switch (command) {
            case CMD_OPEN:
                open(crusher);
                break;
            case CMD_CLOSE:
                close(crusher);
                break;
            case CMD_STATUS:
                status(crusher);
                break;
            case CMD_FREEZE:
                freeze(crusher);
                break;
            case CMD_UNFREEZE:
                unfreeze(crusher);
                break;
            case CMD_HELP:
                printHelp();
                break;
            default:
                LOGGER.warn("Command is unknown: '{}'", command);
                break;
        }
    }

    protected void open(T crusher) throws IOException {
        if (crusher.isOpen()) {
            LOGGER.info("Crusher is already opened");
        } else {
            crusher.open();
            LOGGER.info("Crusher is opened");
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
        if (crusher.isOpen()) {
            LOGGER.info("Crusher is opened");
            if (crusher.isFrozen()) {
                LOGGER.info("Crusher is frozen");
            }
        } else {
            LOGGER.info("Crusher is closed");
        }
    }

    protected void printUsage() {
        LOGGER.info("Execution: {} <bind-socket-address:port> <connect-socket-address:port>",
            this.getClass().getSimpleName());
    }

    protected void printHelp() {
        LOGGER.info("Commands:");
        LOGGER.info("\t" + CMD_OPEN + "     - opens the crusher");
        LOGGER.info("\t" + CMD_CLOSE + "    - closes the crusher (sockets will be closed)");
        LOGGER.info("\t" + CMD_FREEZE + "   - freezes the crusher (open but transfer engine is not working)");
        LOGGER.info("\t" + CMD_UNFREEZE + " - unfreezes the crusher");
        LOGGER.info("\t" + CMD_STATUS + "   - prints the status of the connection");
        LOGGER.info("\t" + CMD_HELP + "     - prints this help");
        LOGGER.info("\t" + CMD_QUIT + "     - quits the program");
    }

    protected abstract T create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException;

}

