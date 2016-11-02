package org.netcrusher.tcp.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpBulkServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpBulkServer.class);

    private final ServerSocketChannel serverSocketChannel;

    private final Acceptor acceptor;

    public TcpBulkServer(InetSocketAddress address, long limit) throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(true);
        this.serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        this.serverSocketChannel.bind(address);

        this.acceptor = new Acceptor(serverSocketChannel, limit);
    }

    public void open() {
        this.acceptor.start();
    }

    @Override
    public void close() throws Exception {
        this.acceptor.interrupt();
        this.acceptor.join();

        this.serverSocketChannel.close();
    }

    public Collection<TcpBulkClient> getClients() {
        return new ArrayList<>(acceptor.clients);
    }

    private static class Acceptor extends Thread {

        private final ServerSocketChannel serverSocketChannel;

        private final Collection<TcpBulkClient> clients;

        private final long limit;

        public Acceptor(ServerSocketChannel serverSocketChannel, long limit) {
            this.serverSocketChannel = serverSocketChannel;
            this.clients = new CopyOnWriteArrayList<>();
            this.limit = limit;

            this.setName("Acceptor thread");
        }

        @Override
        public void run() {
            LOGGER.debug("Accepting thread  has started");

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    SocketChannel channel = serverSocketChannel.accept();
                    LOGGER.debug("Connection is accepted from <{}>", channel.getRemoteAddress());

                    TcpBulkClient client = TcpBulkClient.forSocket("INT-" + clients.size(), channel, limit);
                    clients.add(client);
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
            } catch (IOException e) {
                LOGGER.error("Error while accepting", e);
            }

            LOGGER.debug("Accepting thread has finished");
        }
    }

}
