package org.netcrusher.core;

import org.netcrusher.NetCrusher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

public abstract class AbstractCrusherMain<T extends NetCrusher> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrusherMain.class);

    protected int run(String[] arguments) {
        if (arguments == null || arguments.length != 2) {
            printUsage();
            return 1;
        }

        InetSocketAddress bindAddress;
        try {
            bindAddress = parseInetSocketAddress(arguments[0]);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Fail to parse address: {}", arguments[0]);
            return 2;
        }

        InetSocketAddress connectAddress;
        try {
            connectAddress = parseInetSocketAddress(arguments[1]);
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
                        command(crusher, line);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("REPL error", e);
        }
    }

    protected void command(T crusher, String command) throws IOException {
        switch (command) {
            case "OPEN":
                open(crusher);
                break;
            case "CLOSE":
                close(crusher);
                break;
            case "STATUS":
                status(crusher);
                break;
            case "FREEZE":
                freeze(crusher);
                break;
            case "UNFREEZE":
                unfreeze(crusher);
                break;
            case "HELP":
                LOGGER.info("Commands: OPEN,CLOSE,FREEZE,UNFREEZE,STATUS,HELP,QUIT");
                break;
            default:
                LOGGER.warn("Unknown command: {}", command);
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

    private void printUsage() {
        LOGGER.info("Execution: {} <bind-socket-address:port> <connect-socket-address:port>",
            this.getClass().getSimpleName());
    }

    private static InetSocketAddress parseInetSocketAddress(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Address is empty");
        }

        int index = text.lastIndexOf(':');
        if (index == -1 || index == text.length() - 1) {
            throw new IllegalArgumentException("Port is found in: " + text);
        }
        if (index == 0) {
            throw new IllegalArgumentException("Host is found in: " + text);
        }

        String host = text.substring(0, index);

        String portStr = text.substring(index + 1, text.length());
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port is not integer in address: " + text);
        }

        InetSocketAddress address;
        try {
            address = new InetSocketAddress(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to parse address: " + text);
        }

        return address;
    }

    protected abstract T create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException;

}

