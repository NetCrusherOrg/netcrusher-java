package org.netcrusher.tcp;

import org.netcrusher.common.NioReactor;
import org.netcrusher.common.NioUtils;
import org.netcrusher.filter.ByteBufferFilter;
import org.netcrusher.filter.ByteBufferFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.List;
import java.util.UUID;

public class TcpPair implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpPair.class);

    private final String key;

    private final AbstractSelectableChannel inner;

    private final SelectionKey innerKey;

    private final AbstractSelectableChannel outer;

    private final SelectionKey outerKey;

    private final TcpTransfer innerTransfer;

    private final TcpTransfer outerTransfer;

    private final TcpCrusher crusher;

    private final NioReactor reactor;

    private final InetSocketAddress innerClientAddr;

    private final InetSocketAddress innerListenAddr;

    private final InetSocketAddress outerClientAddr;

    private final InetSocketAddress outerListenAddr;

    private boolean opened;

    private volatile boolean frozen;

    public TcpPair(TcpCrusher crusher, NioReactor reactor, ByteBufferFilterRepository filters,
                   SocketChannel inner, SocketChannel outer,
                   int bufferCount, int bufferSize) throws IOException {
        this.key = UUID.randomUUID().toString();
        this.crusher = crusher;
        this.reactor = reactor;
        this.frozen = true;
        this.opened = true;

        this.inner = inner;
        this.outer = outer;

        this.innerClientAddr = (InetSocketAddress) inner.getRemoteAddress();
        this.innerListenAddr = (InetSocketAddress) inner.getLocalAddress();
        this.outerClientAddr = (InetSocketAddress) outer.getLocalAddress();
        this.outerListenAddr = (InetSocketAddress) outer.getRemoteAddress();

        this.innerKey = reactor.registerSelector(inner, 0, this::innerCallback);
        this.outerKey = reactor.registerSelector(outer, 0, this::outerCallback);

        TcpQueue innerToOuter = new TcpQueue(bufferCount, bufferSize);
        TcpQueue outerToInner = new TcpQueue(bufferCount, bufferSize);

        List<ByteBufferFilter> outgoingFilters = filters.getOutgoing().createFilters(innerClientAddr);
        this.innerTransfer = new TcpTransfer("INNER", this.outerKey, outerToInner, innerToOuter,
            outgoingFilters);

        List<ByteBufferFilter> incomingFilters = filters.getIncoming().createFilters(innerClientAddr);
        this.outerTransfer = new TcpTransfer("OUTER", this.innerKey, innerToOuter, outerToInner,
            incomingFilters);
    }

    /**
     * Start transfer after pair is created
     * @see TcpPair#freeze()
     */
    public synchronized void unfreeze() throws IOException {
        if (opened) {
            if (frozen) {
                reactor.executeSelectorOp(() -> {
                    int ops;

                    ops = innerTransfer.getIncoming().isEmpty() ?
                        SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    innerKey.interestOps(ops);

                    ops = outerTransfer.getIncoming().isEmpty() ?
                        SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    outerKey.interestOps(ops);

                    return null;
                });

                frozen = false;
            }
        } else {
            throw new IllegalStateException("Pair is closed");
        }
    }

    /**
     * Freezes any transfer. Sockets are still open but data are not sent
     * @see TcpPair#unfreeze()
     */
    public synchronized void freeze() throws IOException {
        if (opened) {
            if (!frozen) {
                reactor.executeSelectorOp(() -> {
                    if (innerKey.isValid()) {
                        innerKey.interestOps(0);
                    }

                    if (outerKey.isValid()) {
                        outerKey.interestOps(0);
                    }

                    return null;
                });

                frozen = true;
            }
        } else {
            throw new IllegalStateException("Pair is closed");
        }
    }

    /**
     * Is the pair frozen
     * @return Return true if freeze() on this pair was called before
     * @see TcpPair#unfreeze()
     * @see TcpPair#freeze()
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Closes this paired connection
     */
    @Override
    public synchronized void close() throws IOException {
        if (opened) {
            freeze();

            NioUtils.closeChannel(inner);
            NioUtils.closeChannel(outer);

            opened = false;

            LOGGER.debug("Pair '{}' is closed", this.getKey());
        }
    }

    private void closeInternal() throws IOException {
        reactor.execute(() -> {
            crusher.closePair(this.getKey());
            return null;
        });
    }

    private void callback(SelectionKey selectionKey,
                          AbstractSelectableChannel thisChannel,
                          TcpTransfer thisTransfer,
                          AbstractSelectableChannel thatChannel) throws IOException
    {
        try {
            thisTransfer.handleEvent(selectionKey);
        } catch (EOFException | ClosedChannelException e) {
            LOGGER.trace("EOF on transfer or channel is closed on {}", thisTransfer.getName());
            if (thisTransfer.getOutgoing().pending() > 0) {
                NioUtils.closeChannel(thisChannel);
            } else {
                closeInternal();
            }
        } catch (IOException e) {
            LOGGER.error("IO exception on {}", thisTransfer.getName(), e);
            closeInternal();
        }

        if (thisChannel.isOpen() && !thatChannel.isOpen() && thisTransfer.getIncoming().pending() == 0) {
            closeInternal();
        }
    }

    private void innerCallback(SelectionKey selectionKey) throws IOException {
        callback(selectionKey, inner, innerTransfer, outer);
    }

    private void outerCallback(SelectionKey selectionKey) throws IOException {
        callback(selectionKey, outer, outerTransfer, inner);
    }

    /**
     * Unique identifier for this pair
     * @return Identifier string
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns client address for 'inner' connection
     * @return Socket address
     */
    public InetSocketAddress getInnerClientAddr() {
        return innerClientAddr;
    }

    /**
     * Returns listening address for 'inner' connection
     * @return Socket address
     */
    public InetSocketAddress getInnerListenAddr() {
        return innerListenAddr;
    }

    /**
     * Returns client address for 'outer' connection
     * @return Socket address
     */
    public InetSocketAddress getOuterClientAddr() {
        return outerClientAddr;
    }

    /**
     * Return listening address for 'outer' connection
     * @return Socket address
     */
    public InetSocketAddress getOuterListenAddr() {
        return outerListenAddr;
    }

}


