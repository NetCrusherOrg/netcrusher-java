package org.netcrusher.tcp.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpBulkServer implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpBulkServer.class);

    private final ServerSocketChannel serverSocketChannel;

    private final long count;

    private final Thread thread;

    private final Collection<TcpBulkClient> clients;

    public TcpBulkServer(InetSocketAddress address, long count) throws IOException {
        this.count = count;
        this.clients = new CopyOnWriteArrayList<>();

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(true);
        this.serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        this.serverSocketChannel.bind(address);

        this.thread = new Thread(this::acceptLoop);
        this.thread.setName("Accept loop");
        this.thread.start();
    }

    @Override
    public void close() throws IOException {
        serverSocketChannel.close();

        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Collection<TcpBulkClient> getClients() {
        return clients;
    }

    public void clearClients() {
        clients.clear();
    }

    private void acceptLoop() {
        LOGGER.debug("Accepting thread started");

        while (!Thread.currentThread().isInterrupted()) {
            SocketChannel socketChannel;
            try {
                socketChannel = serverSocketChannel.accept();
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (IOException e) {
                LOGGER.error("Error while accepting", e);
                break;
            }

            TcpBulkClient client = TcpBulkClient.forSocket(socketChannel, count);
            clients.add(client);
        }

        LOGGER.debug("Accepting thread has finished");
    }
}
