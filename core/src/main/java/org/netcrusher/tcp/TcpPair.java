package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
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
import java.util.concurrent.TimeUnit;

public class TcpPair implements NetFreezer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpPair.class);

    private static final long LINGER_PERIOD_MS = 10000;

    private final SocketChannel inner;

    private final SelectionKey innerKey;

    private final SocketChannel outer;

    private final SelectionKey outerKey;

    private final TcpTransfer innerTransfer;

    private final TcpTransfer outerTransfer;

    private final TcpCrusher crusher;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private boolean open;

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
        this.open = true;

        this.inner = inner;
        this.outer = outer;

        this.clientAddress = (InetSocketAddress) inner.getRemoteAddress();

        this.innerKey = reactor.getSelector().register(inner, 0, this::innerCallback);
        this.outerKey = reactor.getSelector().register(outer, 0, this::outerCallback);

        ByteBufferFilter[] outgoingFilters = filters.getOutgoing().createFilters(clientAddress);
        TcpQueue innerToOuter = new TcpQueue(outgoingFilters, bufferCount, bufferSize);
        ByteBufferFilter[] incomingFilters = filters.getIncoming().createFilters(clientAddress);
        TcpQueue outerToInner = new TcpQueue(incomingFilters, bufferCount, bufferSize);

        this.innerTransfer = new TcpTransfer("INNER", this.outerKey, outerToInner, innerToOuter);
        this.outerTransfer = new TcpTransfer("OUTER", this.innerKey, innerToOuter, outerToInner);
    }

    @Override
    public synchronized void unfreeze() throws IOException {
        if (open) {
            if (frozen) {
                reactor.getSelector().executeOp(() -> {
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

    @Override
    public synchronized void freeze() throws IOException {
        if (open) {
            if (!frozen) {
                reactor.getSelector().executeOp(() -> {
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
            LOGGER.debug("Component is closed on freeze");
        }
    }

    @Override
    public synchronized boolean isFrozen() {
        if (open) {
            return frozen;
        } else {
            throw new IllegalStateException("Pair is closed");
        }
    }

    public boolean isOpen() {
        return open;
    }

    synchronized void closeExternal() throws IOException {
        if (open) {
            freeze();

            NioUtils.closeChannel(inner);
            NioUtils.closeChannel(outer);

            open = false;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Pair for '{}' is closed", clientAddress);

                int incomingBytes = innerTransfer.getIncoming().calculateReadyBytes();
                if (incomingBytes > 0) {
                    LOGGER.debug("The pair for {} has {} incoming bytes when closing", incomingBytes);
                }

                int outgoingBytes = innerTransfer.getOutgoing().calculateReadyBytes();
                if (outgoingBytes > 0) {
                    LOGGER.debug("The pair for {} has {} outgoing bytes when closing", outgoingBytes);
                }
            }
        }
    }

    private void closeInternal() throws IOException {
        LOGGER.debug("Pair for <{}> will be self-closed", clientAddress);
        reactor.getScheduler().execute(() -> {
            crusher.closePair(this.getClientAddress());
            return true;
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
            LOGGER.debug("EOF on transfer or channel is closed on {}", thisTransfer.getName());
            if (thisTransfer.getOutgoing().calculateReadyBytes() > 0) {
                NioUtils.closeChannel(thisChannel);
                reactor.getScheduler()
                    .schedule(LINGER_PERIOD_MS, TimeUnit.MILLISECONDS, () -> {
                        this.closeInternal();
                        return true;
                    });
            } else {
                closeInternal();
            }
        } catch (IOException e) {
            LOGGER.debug("IO exception on {}", thisTransfer.getName(), e);
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

}


