package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.nio.NioUtils;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
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
import java.util.concurrent.TimeUnit;

class TcpAcceptor implements NetFreezer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpAcceptor.class);

    private final InetSocketAddress bindAddress;

    private final InetSocketAddress connectAddress;

    private final TcpCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final TcpCrusher crusher;

    private final ServerSocketChannel serverSocketChannel;

    private final SelectionKey serverSelectionKey;

    private final BufferOptions bufferOptions;

    private final TcpFilters filters;

    private final State state;

    TcpAcceptor(
            TcpCrusher crusher, NioReactor reactor,
            InetSocketAddress bindAddress, InetSocketAddress connectAddress,
            TcpCrusherSocketOptions socketOptions, TcpFilters filters,
            BufferOptions bufferOptions) throws IOException
    {
        this.crusher = crusher;
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        this.socketOptions = socketOptions;
        this.reactor = reactor;
        this.bufferOptions = bufferOptions;
        this.filters = filters;

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        if (socketOptions.getBacklog() > 0) {
            this.serverSocketChannel.bind(bindAddress, socketOptions.getBacklog());
        } else {
            this.serverSocketChannel.bind(bindAddress);
        }

        this.serverSelectionKey = reactor.getSelector()
            .register(serverSocketChannel, 0, (selectionKey) -> this.accept());

        this.state = new State(State.FROZEN);
    }

    void close() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                serverSelectionKey.cancel();

                NioUtils.close(serverSocketChannel);

                reactor.getSelector().wakeup();

                state.set(State.CLOSED);

                return true;
            } else {
                return false;
            }
        });
    }

    private void accept() throws IOException {
        final SocketChannel socketChannel1 = serverSocketChannel.accept();
        socketChannel1.configureBlocking(false);
        socketOptions.setupSocketChannel(socketChannel1);
        bufferOptions.checkTcpSocket(socketChannel1.socket());

        LOGGER.debug("Incoming connection is accepted on <{}>", bindAddress);

        final SocketChannel socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);
        socketOptions.setupSocketChannel(socketChannel2);
        bufferOptions.checkTcpSocket(socketChannel2.socket());

        final boolean connectedImmediately;
        try {
            connectedImmediately = socketChannel2.connect(connectAddress);
        } catch (UnresolvedAddressException e) {
            LOGGER.error("Connect address <{}> is unresolved", connectAddress);
            NioUtils.closeNoLinger(socketChannel1);
            NioUtils.closeNoLinger(socketChannel2);
            return;
        } catch (UnsupportedAddressTypeException e) {
            LOGGER.error("Connect address <{}> is unsupported", connectAddress);
            NioUtils.closeNoLinger(socketChannel1);
            NioUtils.closeNoLinger(socketChannel2);
            return;
        } catch (IOException e) {
            LOGGER.error("IOException on connection", e);
            NioUtils.closeNoLinger(socketChannel1);
            NioUtils.closeNoLinger(socketChannel2);
            return;
        }

        if (connectedImmediately) {
            appendPair(socketChannel1, socketChannel2);
        } else {
            connectDeferred(socketChannel1, socketChannel2);
        }
    }

    private void connectDeferred(SocketChannel socketChannel1, SocketChannel socketChannel2) throws IOException {
        if (socketOptions.getConnectionTimeoutMs() > 0) {
            reactor.getSelector().schedule(() -> {
                if (socketChannel2.isOpen() && !socketChannel2.isConnected()) {
                    LOGGER.error("Fail to connect to <{}> in {}ms",
                        connectAddress, socketOptions.getConnectionTimeoutMs());

                    NioUtils.closeNoLinger(socketChannel1);
                    NioUtils.closeNoLinger(socketChannel2);
                }
            }, TimeUnit.MILLISECONDS.toNanos(socketOptions.getConnectionTimeoutMs()));
        }

        reactor.getSelector().register(socketChannel2, SelectionKey.OP_CONNECT, (selectionKey) -> {
            boolean connected;
            try {
                connected = socketChannel2.finishConnect();
            } catch (IOException e) {
                LOGGER.error("Exception while finishing the connection to <{}>", connectAddress,  e);
                connected = false;
            }

            if (!connected) {
                LOGGER.error("Fail to finish outgoing connection to <{}>", connectAddress);
                NioUtils.closeNoLinger(socketChannel1);
                NioUtils.closeNoLinger(socketChannel2);
                return;
            }

            appendPair(socketChannel1, socketChannel2);
        });
    }

    private void appendPair(SocketChannel socketChannel1, SocketChannel socketChannel2) {
        try {
            InetSocketAddress clientAddress = (InetSocketAddress) socketChannel1.getRemoteAddress();

            Runnable pairShutdown = () -> crusher.closeClient(clientAddress);

            TcpPair pair = new TcpPair(reactor, filters, socketChannel1, socketChannel2, bufferOptions, pairShutdown);
            pair.unfreeze();

            crusher.notifyPairCreated(pair);
        } catch (ClosedChannelException | CancelledKeyException e) {
            LOGGER.debug("One of the channels is already closed", e);
            NioUtils.closeNoLinger(socketChannel1);
            NioUtils.closeNoLinger(socketChannel2);
        } catch (IOException e) {
            LOGGER.error("Fail to create TcpCrusher TCP pair", e);
            NioUtils.closeNoLinger(socketChannel1);
            NioUtils.closeNoLinger(socketChannel2);
        }
    }

    @Override
    public void freeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.OPEN)) {
                if (serverSelectionKey.isValid()) {
                    serverSelectionKey.interestOps(0);
                }

                state.set(State.FROZEN);

                LOGGER.debug("TcpCrusher acceptor <{}>-<{}> is frozen", bindAddress, connectAddress);

                return true;
            } else {
                throw new IllegalStateException("Acceptor is not open on freeze");
            }
        });
    }

    @Override
    public void unfreeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.FROZEN)) {
                serverSelectionKey.interestOps(SelectionKey.OP_ACCEPT);

                state.set(State.OPEN);

                LOGGER.debug("TcpCrusher acceptor <{}>-<{}> is unfrozen", bindAddress, connectAddress);

                return true;
            } else {
                throw new IllegalStateException("Acceptor is not frozen on unfreeze");
            }
        });
    }

    @Override
    public boolean isFrozen() {
        return state.isAnyOf(State.FROZEN | State.CLOSED);
    }

    private static final class State extends BitState {

        private static final int OPEN = bit(0);

        private static final int FROZEN = bit(1);

        private static final int CLOSED = bit(2);

        private State(int state) {
            super(state);
        }
    }

}
