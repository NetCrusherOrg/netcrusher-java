package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.reactor.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class TcpAcceptor implements NetFreezer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpAcceptor.class);

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final TcpCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final TcpCrusher crusher;

    private ServerSocketChannel serverSocketChannel;

    private SelectionKey serverSelectionKey;

    private final int bufferCount;

    private final int bufferSize;

    private final TcpFilters filters;

    private volatile boolean open;

    private volatile boolean frozen;

    TcpAcceptor(TcpCrusher crusher, NioReactor reactor,
                       InetSocketAddress bindAddress, InetSocketAddress connectAddress,
                       TcpCrusherSocketOptions socketOptions, TcpFilters filters,
                       int bufferCount, int bufferSize) throws IOException {
        this.crusher = crusher;
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.socketOptions = socketOptions;
        this.reactor = reactor;
        this.bufferCount = bufferCount;
        this.bufferSize = bufferSize;
        this.filters = filters;

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        if (socketOptions.getBacklog() > 0) {
            this.serverSocketChannel.bind(bindAddress, socketOptions.getBacklog());
        } else {
            this.serverSocketChannel.bind(bindAddress);
        }

        serverSelectionKey = reactor.getSelector().register(serverSocketChannel, 0, (selectionKey) -> this.accept());

        this.open = true;
        this.frozen = true;
    }

    void closeExternal() throws IOException {
        serverSelectionKey.cancel();
        serverSelectionKey = null;

        NioUtils.closeChannel(serverSocketChannel);
        serverSocketChannel = null;

        reactor.getSelector().wakeup();
    }

    private void accept() throws IOException {
        final SocketChannel socketChannel1 = serverSocketChannel.accept();
        socketChannel1.configureBlocking(false);
        socketOptions.setupSocketChannel(socketChannel1);

        LOGGER.debug("Incoming connection is accepted on <{}>", bindAddress);

        final SocketChannel socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);
        socketOptions.setupSocketChannel(socketChannel2);

        final boolean connectedNow;
        try {
            connectedNow = socketChannel2.connect(connectAddress);
        } catch (UnresolvedAddressException e) {
            LOGGER.error("Connect address <{}> is unresolved", connectAddress);
            NioUtils.closeChannel(socketChannel1);
            NioUtils.closeChannel(socketChannel2);
            return;
        } catch (UnsupportedAddressTypeException e) {
            LOGGER.error("Connect address <{}> is unsupported", connectAddress);
            NioUtils.closeChannel(socketChannel1);
            NioUtils.closeChannel(socketChannel2);
            return;
        } catch (IOException e) {
            LOGGER.error("IOException on connection", e);
            NioUtils.closeChannel(socketChannel1);
            NioUtils.closeChannel(socketChannel2);
            return;
        }

        if (connectedNow) {
            appendPair(socketChannel1, socketChannel2);
            return;
        }

        final Future<?> connectCheck;
        if (socketOptions.getConnectionTimeoutMs() > 0) {
            connectCheck = reactor.getScheduler().schedule(() -> {
                if (socketChannel2.isOpen() && !socketChannel2.isConnected()) {
                    LOGGER.error("Fail to connect to <{}> in {}ms",
                        connectAddress, socketOptions.getConnectionTimeoutMs());
                    NioUtils.closeChannel(socketChannel1);
                    NioUtils.closeChannel(socketChannel2);
                }
                return true;
            }, socketOptions.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
        } else {
            connectCheck = CompletableFuture.completedFuture(null);
        }

        reactor.getSelector().register(socketChannel2, SelectionKey.OP_CONNECT, (selectionKey) -> {
            connectCheck.cancel(false);

            boolean connected;
            try {
                connected = socketChannel2.finishConnect();
            } catch (IOException e) {
                LOGGER.error("Exception while finishing the connection", e);
                connected = false;
            }

            if (!connected) {
                LOGGER.error("Fail to finish outgoing connection to <{}>", connectAddress);
                NioUtils.closeChannel(socketChannel1);
                NioUtils.closeChannel(socketChannel2);
                return;
            }

            appendPair(socketChannel1, socketChannel2);
        });
    }

    private void appendPair(SocketChannel socketChannel1, SocketChannel socketChannel2) {
        try {
            TcpPair pair = new TcpPair(crusher, reactor, filters,
                socketChannel1, socketChannel2, bufferCount, bufferSize);
            pair.unfreeze();

            crusher.notifyPairCreated(pair);
        } catch (ClosedChannelException | CancelledKeyException e) {
            LOGGER.debug("One of the channels is already closed", e);
            NioUtils.closeChannel(socketChannel1);
            NioUtils.closeChannel(socketChannel2);
        } catch (IOException e) {
            LOGGER.error("Fail to create TcpCrusher TCP pair", e);
            NioUtils.closeChannel(socketChannel1);
            NioUtils.closeChannel(socketChannel2);
        }
    }

    @Override
    public synchronized void freeze() throws IOException {
        if (open) {
            if (!frozen) {
                reactor.getSelector().execute(() -> {
                    if (serverSelectionKey.isValid()) {
                        serverSelectionKey.interestOps(0);
                    }

                    return true;
                });

                frozen = true;

                LOGGER.debug("TcpCrusher acceptor <{}>-<{}> is frozen", bindAddress, connectAddress);
            }
        } else {
            LOGGER.debug("Component is closed on freeze");
        }
    }

    @Override
    public synchronized void unfreeze() throws IOException {
        if (open) {
            if (frozen) {
                reactor.getSelector().execute(() ->
                    serverSelectionKey.interestOps(SelectionKey.OP_ACCEPT));

                frozen = false;

                LOGGER.debug("TcpCrusher acceptor <{}>-<{}> is unfrozen", bindAddress, connectAddress);
            }
        } else {
            throw new IllegalStateException("Crusher is not open");
        }
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }
}
