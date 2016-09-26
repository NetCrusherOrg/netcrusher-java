package org.netcrusher.datagram;

import org.netcrusher.common.NioReactor;
import org.netcrusher.common.NioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DatagramInner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramInner.class);

    private final DatagramCrusher crusher;

    private final DatagramCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    private final long maxIdleDurationMs;

    private final DatagramChannel channel;

    private final SelectionKey selectionKey;

    private final ByteBuffer bb;

    private final Map<InetSocketAddress, DatagramOuter> outers;

    private final DatagramQueue incoming;

    private volatile boolean frozen;

    public DatagramInner(DatagramCrusher crusher,
                         InetSocketAddress localAddress,
                         InetSocketAddress remoteAddress,
                         DatagramCrusherSocketOptions socketOptions,
                         long maxIdleDurationMs) throws IOException {
        this.crusher = crusher;
        this.reactor = crusher.getReactor();
        this.socketOptions = socketOptions;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.outers = new HashMap<>(32);
        this.incoming = new DatagramQueue();
        this.maxIdleDurationMs = maxIdleDurationMs;
        this.frozen = true;

        this.channel = DatagramChannel.open(socketOptions.getProtocolFamily());
        this.channel.configureBlocking(true);
        socketOptions.setupSocketChannel(this.channel);
        this.channel.bind(localAddress);
        this.channel.configureBlocking(false);

        this.bb = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

        this.selectionKey = reactor.register(channel, 0, this::callback);

        LOGGER.debug("Inner on <{}> is started", localAddress);
    }

    protected synchronized void unfreeze() throws IOException {
        if (frozen) {
            reactor.executeReactorOp(() -> {
                int ops = incoming.size() > 0 ?
                    SelectionKey.OP_READ | SelectionKey.OP_WRITE : SelectionKey.OP_READ;
                selectionKey.interestOps(ops);

                outers.values().forEach(DatagramOuter::unfreeze);

                return null;
            });

            frozen = false;
        }
    }

    protected synchronized void freeze() throws IOException {
        if (!frozen) {
            reactor.executeReactorOp(() -> {
                if (selectionKey.isValid()) {
                    selectionKey.interestOps(0);
                }

                outers.values().forEach(DatagramOuter::freeze);

                return null;
            });

            frozen = true;
        }
    }

    protected boolean isFrozen() {
        return frozen;
    }

    protected synchronized void close() throws IOException {
        freeze();

        if (!incoming.isEmpty()) {
            LOGGER.warn("On closing inner has {} incoming datagrams with {} bytes in total",
                incoming.size(), incoming.remaining());
        }

        outers.values().forEach(DatagramOuter::close);
        outers.clear();

        NioUtils.closeChannel(channel);

        reactor.wakeup();

        LOGGER.debug("Inner on <{}> is closed", localAddress);
    }

    private void closeInternal() throws IOException {
        crusher.removeInner();
        close();
    }

    private void callback(SelectionKey selectionKey) throws IOException {
        try {
            if (selectionKey.isReadable()) {
                handleReadable(selectionKey);
            }
            if (selectionKey.isWritable()) {
                handleWritable(selectionKey);
            }
        } catch (ClosedChannelException e) {
            LOGGER.debug("Inner has closed itself");
            closeInternal();
        } catch (IOException e) {
            LOGGER.error("Exception in inner", e);
            closeInternal();
        }
    }

    private void handleWritable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        DatagramQueue.Entry entry;
        while ((entry = incoming.request()) != null) {
            int written = channel.send(entry.getBuffer(), entry.getAddress());
            if (written == 0) {
                incoming.retry(entry);
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send {} bytes to client <{}>", written, entry.getAddress());
            }

            if (entry.getBuffer().hasRemaining()) {
                LOGGER.warn("Datagram is splitted");
                incoming.retry(entry);
            } else {
                incoming.release(entry);
            }
        }

        if (incoming.isEmpty()) {
            NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    private void handleReadable(SelectionKey selectionKey) throws IOException {
        DatagramChannel channel = (DatagramChannel) selectionKey.channel();

        while (true) {
            InetSocketAddress address = (InetSocketAddress) channel.receive(bb);
            if (address == null) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received {} bytes from inner <{}>", bb.limit(), address);
            }

            DatagramOuter outer = requestOuter(address);

            bb.flip();
            outer.enqueue(bb);
            bb.clear();
        }
    }

    private DatagramOuter requestOuter(InetSocketAddress address) throws IOException {
        DatagramOuter outer = outers.get(address);

        if (outer == null) {
            if (maxIdleDurationMs > 0) {
                clearOuters(maxIdleDurationMs);
            }

            outer = new DatagramOuter(this, address, remoteAddress, socketOptions);
            outer.unfreeze();

            outers.put(address, outer);
        }

        return outer;
    }

    private void clearOuters(long maxIdleDurationMs) {
        int countBefore = outers.size();
        if (countBefore > 0) {
            Iterator<DatagramOuter> outerIterator = outers.values().iterator();

            while (outerIterator.hasNext()) {
                DatagramOuter outer = outerIterator.next();

                if (outer.getIdleDurationMs() > maxIdleDurationMs) {
                    outer.close();
                    outerIterator.remove();
                }
            }

            int countAfter = outers.size();
            LOGGER.debug("Outer connections are cleared ({} -> {})", countBefore, countAfter);
        }
    }

    protected void enqueue(InetSocketAddress address, ByteBuffer bbToCopy) {
        boolean added = incoming.add(address, bbToCopy);
        if (added) {
            NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
        }
    }

    protected NioReactor getReactor() {
        return reactor;
    }

    protected void removeOuter(InetSocketAddress clientAddress) {
        outers.remove(clientAddress);
    }

}
