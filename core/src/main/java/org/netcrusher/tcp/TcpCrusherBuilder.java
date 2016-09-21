package org.netcrusher.tcp;

import org.netcrusher.common.NioReactor;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

public final class TcpCrusherBuilder {

    private InetSocketAddress localAddress;

    private InetSocketAddress remoteAddress;

    private NioReactor reactor;

    private TcpCrusherSocketOptions socketOptions;

    private Consumer<TcpPair> creationListener;

    private Consumer<TcpPair> deletionListener;

    private int bufferCount;

    private int bufferSize;

    private TcpCrusherBuilder() {
        this.socketOptions = new TcpCrusherSocketOptions();
        this.bufferCount = 16;
        this.bufferSize = 16 * 1024;
    }

    public static TcpCrusherBuilder builder() {
        return new TcpCrusherBuilder();
    }

    public TcpCrusherBuilder withLocalAddress(InetSocketAddress address) {
        this.localAddress = address;
        return this;
    }

    public TcpCrusherBuilder withLocalAddress(String hostname, int port) {
        this.localAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    public TcpCrusherBuilder withRemoteAddress(InetSocketAddress address) {
        this.remoteAddress = address;
        return this;
    }

    public TcpCrusherBuilder withRemoteAddress(String hostname, int port) {
        this.remoteAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    public TcpCrusherBuilder withReactor(NioReactor reactor) {
        this.reactor = reactor;
        return this;
    }

    public TcpCrusherBuilder withCreationListener(Consumer<TcpPair> creationListener) {
        this.creationListener = creationListener;
        return this;
    }

    public TcpCrusherBuilder withDeletionListener(Consumer<TcpPair> deletionListener) {
        this.deletionListener = deletionListener;
        return this;
    }

    public TcpCrusherBuilder withBacklog(int backlog) {
        this.socketOptions.setBacklog(backlog);
        return this;
    }

    public TcpCrusherBuilder withKeepAlive(boolean keepAlive) {
        this.socketOptions.setKeepAlive(keepAlive);
        return this;
    }

    public TcpCrusherBuilder withTcpNoDelay(boolean tcpNoDelay) {
        this.socketOptions.setTcpNoDelay(tcpNoDelay);
        return this;
    }

    public TcpCrusherBuilder withRcvBufferSize(int bufferSize) {
        this.socketOptions.setRcvBufferSize(bufferSize);
        return this;
    }

    public TcpCrusherBuilder withSndBufferSize(int bufferSize) {
        this.socketOptions.setSndBufferSize(bufferSize);
        return this;
    }

    public TcpCrusherBuilder withConnectionTimeoutMs(long timeoutMs) {
        this.socketOptions.setConnectionTimeoutMs(timeoutMs);
        return this;
    }

    public TcpCrusherBuilder withBufferCount(int bufferCount) {
        this.bufferCount = bufferCount;
        return this;
    }

    public TcpCrusherBuilder withBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    public TcpCrusher build() {
        if (localAddress == null) {
            throw new IllegalArgumentException("Local address is not set");
        }

        if (remoteAddress == null) {
            throw new IllegalArgumentException("Remote address is not set");
        }

        if (reactor == null) {
            throw new IllegalArgumentException("Context is not set");
        }

        return new TcpCrusher(localAddress, remoteAddress, socketOptions.copy(), reactor,
            creationListener, deletionListener, bufferCount, bufferSize);
    }
}
