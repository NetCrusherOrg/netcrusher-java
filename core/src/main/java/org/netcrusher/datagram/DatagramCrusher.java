package org.netcrusher.datagram;

import org.netcrusher.common.NioReactor;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

public class DatagramCrusher implements Closeable {

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    private final DatagramCrusherSocketOptions socketOptions;

    private final NioReactor reactor;

    private final long maxIdleDurationMs;

    private DatagramInner inner;

    private volatile boolean opened;

    public DatagramCrusher(InetSocketAddress localAddress,
                           InetSocketAddress remoteAddress,
                           DatagramCrusherSocketOptions socketOptions,
                           NioReactor reactor,
                           long maxIdleDurationMs) {
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.socketOptions = socketOptions;
        this.reactor = reactor;
        this.maxIdleDurationMs = maxIdleDurationMs;
        this.opened = false;
    }

    public synchronized void open() throws IOException {
        if (opened) {
            throw new IllegalStateException("DatagramCrusher is already active");
        }

        this.inner = new DatagramInner(reactor, localAddress, remoteAddress, socketOptions, maxIdleDurationMs);

        this.opened = true;
    }

    @Override
    public synchronized void close() {
        if (!opened) {
            return;
        }

        this.inner.close();

        this.opened = false;
    }

    public synchronized void reopen() throws IOException {
        close();
        open();
    }

}
