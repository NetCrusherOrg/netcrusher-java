package org.netcrusher.core;

import org.netcrusher.NetCrusher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

public abstract class AbstractCrusherMain {

    protected int run(String[] arguments) {
        if (arguments == null || arguments.length != 2) {
            printUsage();
            return 1;
        }

        InetSocketAddress bindAddress;
        try {
            bindAddress = parseInetSocketAddress(arguments[0]);
        } catch (IllegalArgumentException e) {
            System.err.printf("Fail to parse address: %s\n", arguments[0]);
            return 2;
        }

        InetSocketAddress connectAddress;
        try {
            connectAddress = parseInetSocketAddress(arguments[1]);
        } catch (IllegalArgumentException e) {
            System.err.printf("Fail to parse address: %s\n", arguments[1]);
            return 2;
        }

        NioReactor reactor;
        try {
            reactor = new NioReactor();
        } catch (Exception e) {
            System.err.printf("Fail to create reactor: %s\n", e.getLocalizedMessage());
            return 3;
        }

        NetCrusher crusher;
        try {
            crusher = create(reactor, bindAddress, connectAddress);
        } catch (Exception e) {
            System.err.printf("Fail to create crusher: %s\n", e.getLocalizedMessage());
            return 3;
        }

        System.out.println("Print `HELP` for list of commands");
        repl(crusher);

        try {
            close(crusher);
        } catch (Exception e) {
            System.err.printf("Fail to close crusher: %s\n", e.getLocalizedMessage());
        }

        try {
            reactor.close();
        } catch (Exception e) {
            System.err.printf("Fail to close reactor: %s\n", e.getLocalizedMessage());
        }

        return 0;
    }

    protected void repl(NetCrusher crusher) {
        try {
            try (InputStreamReader isr = new InputStreamReader(System.in);
                 BufferedReader br = new BufferedReader(isr))
            {
                while (true) {
                    System.out.printf("$ ");

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
            System.err.printf("REPL error: %s\n", e.getLocalizedMessage());
        }
    }

    protected void command(NetCrusher crusher, String command) throws IOException {
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
                System.out.println("Commands: OPEN,CLOSE,FREEZE,UNFREEZE,STATUS,HELP,QUIT");
                break;
            default:
                System.err.printf("Unknown command: %s\n", command);
                break;
        }
    }

    protected void open(NetCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            System.out.println("Crusher is already opened");
        } else {
            crusher.open();
            System.out.println("Crusher is opened");
        }
    }

    protected void close(NetCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.close();
            System.out.println("Crusher is closed");
        } else {
            System.out.println("Crusher is already closed");
        }
    }

    protected void freeze(NetCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.freeze();
            System.out.println("Crusher is freezed");
        } else {
            System.out.println("Crusher is closed");
        }
    }

    protected void unfreeze(NetCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            crusher.unfreeze();
            System.out.println("Crusher is freezed");
        } else {
            System.out.println("Crusher is closed");
        }
    }

    protected void status(NetCrusher crusher) throws IOException {
        if (crusher.isOpen()) {
            System.out.println("Crusher is opened");
            if (crusher.isFrozen()) {
                System.out.println("Crusher is frozen");
            }
        } else {
            System.out.println("Crusher is closed");
        }
    }

    private void printUsage() {
        System.out.printf("Execution: %s <bind-socket-address:port> <connect-socket-address:port>\n",
            this.getClass().getSimpleName());
    }

    private static InetSocketAddress parseInetSocketAddress(String text) {
        int index = text.lastIndexOf(':');
        if (index == -1 || index == text.length() - 1) {
            throw new IllegalArgumentException("Port is found");
        }
        if (index == 0) {
            throw new IllegalArgumentException("Host is found");
        }

        String host = text.substring(0, index);

        String portStr = text.substring(index + 1, text.length());
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port is not integer");
        }

        InetSocketAddress address;
        try {
            address = new InetSocketAddress(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to parse address");
        }

        return address;
    }

    protected abstract NetCrusher create(NioReactor reactor,
                                         InetSocketAddress bindAddress,
                                         InetSocketAddress connectAddress) throws IOException;

}

