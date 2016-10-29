package org.netcrusher.tcp;

import org.netcrusher.NetFreezer;
import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.state.BitState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

class TcpPair implements NetFreezer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpPair.class);

    private final TcpChannel innerChannel;

    private final TcpChannel outerChannel;

    private final Closeable ownerClose;

    private final NioReactor reactor;

    private final InetSocketAddress clientAddress;

    private final State state;

    TcpPair(
        NioReactor reactor,
        TcpFilters filters,
        SocketChannel inner,
        SocketChannel outer,
        BufferOptions bufferOptions,
        Closeable ownerClose) throws IOException
    {
        this.ownerClose = ownerClose;
        this.reactor = reactor;

        this.clientAddress = (InetSocketAddress) inner.getRemoteAddress();

        TcpQueue innerToOuter = new TcpQueue(clientAddress,
            filters.getOutgoingTransformFilter(), filters.getOutgoingThrottler(),
            bufferOptions);
        TcpQueue outerToInner = new TcpQueue(clientAddress,
            filters.getIncomingTransformFilter(), filters.getIncomingThrottler(),
            bufferOptions);

        this.innerChannel = new TcpChannel("INNER", reactor, this::closeAll, inner,
            outerToInner, innerToOuter);
        this.outerChannel = new TcpChannel("OUTER", reactor, this::closeAll, outer,
            innerToOuter, outerToInner);

        this.innerChannel.setOther(outerChannel);
        this.outerChannel.setOther(innerChannel);

        this.state = new State(State.FROZEN);
    }

    private void closeAll() throws IOException {
        this.close();

        reactor.getScheduler().execute(() -> {
            ownerClose.close();
            return true;
        });
    }

    void close() throws IOException {
        if (state.lockIfNot(State.CLOSED)) {
            try {
                if (state.is(State.OPEN)) {
                    freeze();
                }

                innerChannel.close();
                outerChannel.close();

                state.set(State.CLOSED);

                LOGGER.debug("Pair for '{}' is closed", clientAddress);
            } finally {
                state.unlock();
            }
        }
    }

    @Override
    public void freeze() throws IOException {
        if (state.lockIf(State.OPEN)) {
            try {
                reactor.getSelector().execute(() -> {
                    if (!innerChannel.isFrozen()) {
                        innerChannel.freeze();
                    }

                    if (!outerChannel.isFrozen()) {
                        outerChannel.freeze();
                    }

                    return true;
                });

                state.set(State.FROZEN);
            } finally {
                state.unlock();
            }
        } else {
            if (!isFrozen()) {
                throw new IllegalStateException("Pair is not finally frozen: " + state);
            }
        }
    }

    @Override
    public void unfreeze() throws IOException {
        if (state.lockIf(State.FROZEN)) {
            try {
                reactor.getSelector().execute(() -> {
                    if (innerChannel.isFrozen()) {
                        innerChannel.unfreeze();
                    }

                    if (outerChannel.isFrozen()) {
                        outerChannel.unfreeze();
                    }

                    return true;
                });

                state.set(State.OPEN);
            } finally {
                state.unlock();
            }
        } else {
            if (isFrozen()) {
                throw new IllegalStateException("Pair is not finally unfrozen: " + state);
            }
        }
    }

    @Override
    public boolean isFrozen() {
        return state.isAnyOf(State.FROZEN | State.CLOSED);
    }

    InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    RateMeters getByteMeters() {
        return new RateMeters(innerChannel.getSentMeter(), outerChannel.getSentMeter());
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
