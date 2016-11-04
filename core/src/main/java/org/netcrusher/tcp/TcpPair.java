package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

class TcpPair implements NetFreezer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpPair.class);

    private final TcpChannel innerChannel;

    private final TcpChannel outerChannel;

    private final Runnable ownerClose;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private final State state;

    TcpPair(
        NioReactor reactor,
        TcpFilters filters,
        SocketChannel inner,
        SocketChannel outer,
        BufferOptions bufferOptions,
        Runnable ownerClose) throws IOException
    {
        this.ownerClose = ownerClose;
        this.reactor = reactor;

        this.clientAddress = (InetSocketAddress) inner.getRemoteAddress();

        TcpQueue innerToOuter = TcpQueue.allocateQueue(clientAddress, bufferOptions,
            filters.getOutgoingTransformFilterFactory(), filters.getOutgoingThrottlerFactory());
        TcpQueue outerToInner = TcpQueue.allocateQueue(clientAddress, bufferOptions,
            filters.getIncomingTransformFilterFactory(), filters.getIncomingThrottlerFactory());

        this.innerChannel = new TcpChannel("INNER", reactor, this::closeAll, inner,
            outerToInner, innerToOuter);
        this.outerChannel = new TcpChannel("OUTER", reactor, this::closeAll, outer,
            innerToOuter, outerToInner);

        this.innerChannel.setOther(outerChannel);
        this.outerChannel.setOther(innerChannel);

        this.state = new State(State.FROZEN);
    }

    private void closeAll() {
        this.close();
        ownerClose.run();
    }

    void close() {
        reactor.getSelector().execute(() -> {
            if (state.not(State.CLOSED)) {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                innerChannel.close();
                outerChannel.close();

                state.set(State.CLOSED);

                LOGGER.debug("Pair for '{}' is closed", clientAddress);

                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    public void freeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.OPEN)) {
                innerChannel.freeze();
                outerChannel.freeze();

                state.set(State.FROZEN);

                return true;
            } else {
                throw new IllegalStateException("Pair is not open on freeze");
            }
        });
    }

    @Override
    public void unfreeze() {
        reactor.getSelector().execute(() -> {
            if (state.is(State.FROZEN)) {
                innerChannel.unfreeze();
                outerChannel.unfreeze();

                state.set(State.OPEN);

                return true;
            } else {
                throw new IllegalStateException("Pair is not frozen on unfreeze");
            }
        });
    }

    @Override
    public boolean isFrozen() {
        return state.isAnyOf(State.FROZEN | State.CLOSED);
    }

    InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    RateMeters getByteMeters() {
        return new RateMeters(innerChannel.getSentBytesMeter(), outerChannel.getSentBytesMeter());
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
