package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.NioReactor;
import org.netcrusher.core.NioUtils;
import org.netcrusher.core.filter.ByteBufferFilter;
import org.netcrusher.core.filter.ByteBufferFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TcpPair implements NetFreezer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpPair.class);

    private final SocketChannel inner;

    private final SocketChannel outer;

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

        ByteBufferFilter[] outgoingFilters = filters.getOutgoing().createFilters(clientAddress);
        TcpQueue innerToOuter = new TcpQueue(outgoingFilters, bufferCount, bufferSize);
        ByteBufferFilter[] incomingFilters = filters.getIncoming().createFilters(clientAddress);
        TcpQueue outerToInner = new TcpQueue(incomingFilters, bufferCount, bufferSize);

        this.innerTransfer = new TcpTransfer("INNER", reactor, this::closeInternal, inner, outerToInner, innerToOuter);
        this.outerTransfer = new TcpTransfer("OUTER", reactor, this::closeInternal, outer, innerToOuter, outerToInner);

        this.innerTransfer.setOther(outerTransfer);
        this.outerTransfer.setOther(innerTransfer);
    }

    private void closeInternal() throws IOException {
        LOGGER.debug("Pair for <{}> will be self-closed", clientAddress);
        reactor.getScheduler().execute(() -> {
            crusher.closePair(this.getClientAddress());
            return true;
        });
    }

    @Override
    public synchronized void freeze() throws IOException {
        if (open) {
            if (!frozen) {
                reactor.getSelector().executeOp(() -> {
                    innerTransfer.freeze();
                    outerTransfer.freeze();
                    return true;
                });
                frozen = true;
            }
        } else {
            LOGGER.debug("Component is closed on freeze");
        }
    }

    @Override
    public synchronized void unfreeze() throws IOException {
        if (open) {
            if (frozen) {
                reactor.getSelector().executeOp(() -> {
                    innerTransfer.unfreeze();
                    outerTransfer.unfreeze();
                    return true;
                });
                frozen = false;
            }
        } else {
            throw new IllegalStateException("Pair is closed");
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
     * Check is TCP pair open
     * @return Returns <em>true</em> if the pair is open
     */
    public boolean isOpen() {
        return open;
    }

}


