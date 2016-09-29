package org.netcrusher.tcp;

import org.netcrusher.common.NioReactor;
import org.netcrusher.common.NioUtils;
import org.netcrusher.filter.ByteBufferFilter;
import org.netcrusher.filter.ByteBufferFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;

public class TcpPair {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpPair.class);

    private final SocketChannel inner;

    private final SelectionKey innerKey;

    private final SocketChannel outer;

    private final SelectionKey outerKey;

    private final TcpTransfer innerTransfer;

    private final TcpTransfer outerTransfer;

    private final TcpCrusher crusher;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private boolean opened;

    private volatile boolean frozen;

    TcpPair(
            TcpCrusher crusher,
            NioReactor reactor,
            ByteBufferFilterRepository filters,
            SocketChannel inner,
            SocketChannel outer,
            int bufferCount,
            int bufferSize) throws IOException
    {
        this.crusher = crusher;
        this.reactor = reactor;
        this.frozen = true;
        this.opened = true;

        this.inner = inner;
        this.outer = outer;

        this.clientAddress = (InetSocketAddress) inner.getRemoteAddress();

        this.innerKey = reactor.registerSelector(inner, 0, this::innerCallback);
        this.outerKey = reactor.registerSelector(outer, 0, this::outerCallback);

        ByteBufferFilter[] outgoingFilters = filters.getOutgoing().createFilters(clientAddress);
        TcpQueue innerToOuter = new TcpQueue(outgoingFilters, bufferCount, bufferSize);
        ByteBufferFilter[] incomingFilters = filters.getIncoming().createFilters(clientAddress);
        TcpQueue outerToInner = new TcpQueue(incomingFilters, bufferCount, bufferSize);

        this.innerTransfer = new TcpTransfer("INNER", this.outerKey, outerToInner, innerToOuter);
        this.outerTransfer = new TcpTransfer("OUTER", this.innerKey, innerToOuter, outerToInner);
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

                    ops = innerTransfer.getIncoming().calculateReadyBytes() == 0 ?
                        SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    innerKey.interestOps(ops);

                    ops = outerTransfer.getIncoming().calculateReadyBytes() == 0 ?
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
     * Closes this paired connection
     */
    public synchronized void close() throws IOException {
        if (opened) {
            freeze();

            NioUtils.closeChannel(inner);
            NioUtils.closeChannel(outer);

            opened = false;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Pair for '{}' is closed", clientAddress);

                int incomingBytes = innerTransfer.getIncoming().calculateReadyBytes();
                if (incomingBytes > 0) {
                    LOGGER.debug("On closing pair for {} has {} incoming bytes", incomingBytes);
                }

                int outgoingBytes = innerTransfer.getOutgoing().calculateReadyBytes();
                if (outgoingBytes > 0) {
                    LOGGER.debug("On closing pair for {} has {} outgoing bytes", outgoingBytes);
                }
            }
        }
    }

    private void closeInternal() throws IOException {
        reactor.execute(() -> {
            crusher.closePair(this.getClientAddress());
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
            if (thisTransfer.getOutgoing().calculateReadyBytes() > 0) {
                NioUtils.closeChannel(thisChannel);
            } else {
                closeInternal();
            }
        } catch (IOException e) {
            LOGGER.error("IO exception on {}", thisTransfer.getName(), e);
            closeInternal();
        }

        if (thisChannel.isOpen() && !thatChannel.isOpen() && thisTransfer.getIncoming().calculateReadyBytes() == 0) {
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
     * Returns client address for 'inner' connection
     * @return Socket address
     */
    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    /**
     * Get inner socket statistics
     * @return Inner socket statistics
     */
    public TcpTransfer getInnerTransfer() {
        return innerTransfer;
    }

    /**
     * Get outer socket statistics
     * @return Outer socket statistics
     */
    public TcpTransfer getOuterTransfer() {
        return outerTransfer;
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

}


