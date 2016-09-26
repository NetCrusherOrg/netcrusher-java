package org.netcrusher.tcp;

import org.netcrusher.common.NioReactor;
import org.netcrusher.common.NioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * <p>TcpCrusher - a TCP proxy for test purposes. To create a new instance use TcpCrusherBuilder</p>
 *
 * <pre>
 * NioReactor reactor = new NioReactor();
 * TcpCrusher crusher = TcpCrusherBuilder.builder()
 *     .withReactor(reactor)
 *     .withLocalAddress("localhost", 10080)
 *     .withRemoteAddress("google.com", 80)
 *     .buildAndOpen();
 *
 * // do some test on localhost:10080
 * crusher.crush();
 * // do other test on localhost:10080
 *
 * crusher.close();
 * reactor.close();
 * </pre>
 *
 * @see TcpCrusherBuilder
 * @see NioReactor
 */
public class TcpCrusher implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpCrusher.class);

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    private final TcpCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final Map<String, TcpPair> pairs;

    private final Consumer<TcpPair> creationListener;

    private final Consumer<TcpPair> deletionListener;

    private final int bufferCount;

    private final int bufferSize;

    private ServerSocketChannel serverSocketChannel;

    private SelectionKey serverSelectionKey;

    private volatile boolean opened;

    private volatile boolean frozen;

    protected TcpCrusher(InetSocketAddress localAddress,
                         InetSocketAddress remoteAddress,
                         TcpCrusherSocketOptions socketOptions,
                         NioReactor reactor,
                         Consumer<TcpPair> creationListener,
                         Consumer<TcpPair> deletionListener,
                         int bufferCount,
                         int bufferSize)
    {
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.reactor = reactor;
        this.socketOptions = socketOptions;
        this.opened = false;
        this.pairs = new ConcurrentHashMap<>(32);
        this.bufferCount = bufferCount;
        this.bufferSize = bufferSize;
        this.creationListener = creationListener;
        this.deletionListener = deletionListener;
        this.frozen = true;
    }

    /**
     * Start proxy with specified settings
     * @throws IOException When socket binding fails
     */
    public synchronized void open() throws IOException {
        if (opened) {
            throw new IllegalStateException("TcpCrusher is already active");
        }

        LOGGER.debug("TcpCrusher <{}>-<{}> will be opened", localAddress, remoteAddress);

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        if (socketOptions.getBacklog() > 0) {
            this.serverSocketChannel.bind(localAddress, socketOptions.getBacklog());
        } else {
            this.serverSocketChannel.bind(localAddress);
        }

        serverSelectionKey = reactor.register(serverSocketChannel, 0, (selectionKey) -> this.accept());

        LOGGER.debug("TcpCrusher <{}>-<{}> is opened", localAddress, remoteAddress);

        opened = true;

        unfreeze();
    }

    /**
     * Closes crusher proxy
     */
    @Override
    public synchronized void close() throws IOException {
        if (opened) {
            LOGGER.debug("TcpCrusher <{}>-<{}> will be closed", localAddress, remoteAddress);

            freeze();

            closeAllPairs();

            serverSelectionKey.cancel();
            NioUtils.closeChannel(serverSocketChannel);

            reactor.wakeup();

            LOGGER.debug("TcpCrusher <{}>-<{}> is closed", localAddress, remoteAddress);

            opened = false;
        }
    }

    /**
     * Close all pairs but keeps listening socket open
     */
    public synchronized void closeAllPairs() throws IOException {
        if (opened) {
            for (TcpPair pair : getPairs()) {
                pair.close();
            }
        }
    }

    /**
     * Reopens (closes and the opens again) crusher proxy
     */
    public synchronized void crush() throws IOException {
        if (opened) {
            close();
            open();
        }
    }

    /**
     * Freezes crusher proxy. Call freeze() on every tranfer pair
     * @see TcpCrusher#unfreeze()
     * @see TcpPair#unfreeze()
     * @see TcpPair#freeze()
     * @see TcpPair#isFrozen()
     * @throws IOException On IO error
     */
    public synchronized void freeze() throws IOException {
        if (opened) {
            LOGGER.debug("TcpCrusher <{}>-<{}> will be frozen", localAddress, remoteAddress);

            if (!frozen) {
                reactor.executeReactorOp(() -> serverSelectionKey.interestOps(0));
                frozen = true;
            }

            for (TcpPair pair : pairs.values()) {
                pair.freeze();
            }

            LOGGER.debug("TcpCrusher <{}>-<{}> is frozen", localAddress, remoteAddress);
        }
    }

    /**
     * Resumes crusher proxy after freezing. Call resume() on every tranfer pair
     * @see TcpCrusher#freeze()
     * @see TcpPair#freeze()
     * @see TcpPair#unfreeze()
     * @see TcpPair#isFrozen()
     * @throws IOException On IO error
     */
    public synchronized void unfreeze() throws IOException {
        if (opened) {
            LOGGER.debug("TcpCrusher <{}>-<{}> will be unfrozen", localAddress, remoteAddress);

            for (TcpPair pair : pairs.values()) {
                pair.unfreeze();
            }

            if (frozen) {
                reactor.executeReactorOp(() -> serverSelectionKey.interestOps(SelectionKey.OP_ACCEPT));
                frozen = false;
            }

            LOGGER.debug("TcpCrusher <{}>-<{}> is unfrozen", localAddress, remoteAddress);
        }
    }

    /**
     * Check is the crusher active
     * @return Return 'true' if crusher proxy is active
     */
    public boolean isOpened() {
        return opened;
    }

    protected void accept() throws IOException {
        SocketChannel socketChannel1 = serverSocketChannel.accept();
        socketChannel1.configureBlocking(false);
        socketOptions.setupSocketChannel(socketChannel1);

        LOGGER.debug("Incoming connection is accepted on <{}>", localAddress);

        SocketChannel socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);
        socketOptions.setupSocketChannel(socketChannel2);

        boolean connected = socketChannel2.connect(remoteAddress);
        if (!connected) {
            final Future<?> connectCheck;
            if (socketOptions.getConnectionTimeoutMs() > 0) {
                connectCheck = reactor.schedule(socketOptions.getConnectionTimeoutMs(), () -> {
                    if (socketChannel2.isOpen() && !socketChannel2.isConnected()) {
                        LOGGER.warn("Fail to connect to <{}> in {}ms",
                            remoteAddress, socketOptions.getConnectionTimeoutMs());
                        NioUtils.closeChannel(socketChannel1);
                        NioUtils.closeChannel(socketChannel2);
                    }
                });
            } else {
                connectCheck = CompletableFuture.completedFuture(null);
            }

            reactor.register(socketChannel2, SelectionKey.OP_CONNECT, (selectionKey) -> {
                connectCheck.cancel(false);

                if (!socketChannel2.finishConnect()) {
                    LOGGER.warn("Fail to finish outgoing connection to <{}>", remoteAddress);
                    NioUtils.closeChannel(socketChannel1);
                    NioUtils.closeChannel(socketChannel2);
                    return;
                }

                appendPair(socketChannel1, socketChannel2);
            });
        } else {
            appendPair(socketChannel1, socketChannel2);
        }
    }

    protected void appendPair(SocketChannel socketChannel1, SocketChannel socketChannel2) {
        try {
            TcpPair pair = new TcpPair(this, socketChannel1, socketChannel2, bufferCount, bufferSize);
            pair.unfreeze();

            LOGGER.debug("Pair '{}' is created for <{}>-<{}>",
                new Object[] { pair.getKey(), localAddress, remoteAddress });

            pairs.put(pair.getKey(), pair);

            if (creationListener != null) {
                reactor.execute(() -> creationListener.accept(pair));
            }
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

    protected void removePair(String pairKey) {
        TcpPair pair = pairs.remove(pairKey);

        if (pair != null && deletionListener != null) {
            reactor.execute(() -> deletionListener.accept(pair));
        }
    }

    protected NioReactor getReactor() {
        return reactor;
    }

    /**
     * Get transfer pair by it's key
     * @param pairKey The key of the requested pair
     * @return Transfer pair
     */
    public TcpPair getPair(String pairKey) {
        return pairs.get(pairKey);
    }

    /**
     * Get collection of active tranfer pairs
     * @return Collection of tranfer pairs
     */
    public Collection<TcpPair> getPairs() {
        return new ArrayList<>(this.pairs.values());
    }

    /**
     * Get local listening address
     * @return Inet address
     */
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * Get remote listening address
     * @return Inet address
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

}

