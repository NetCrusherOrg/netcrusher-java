package org.netcrusher.datagram;

import org.netcrusher.common.NioReactor;

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;

public final class DatagramCrusherBuilder {

    private InetSocketAddress localAddress;

    private InetSocketAddress remoteAddress;

    private NioReactor reactor;

    private DatagramCrusherSocketOptions socketOptions;

    private long maxIdleDurationMs;

    private DatagramCrusherBuilder() {
        this.socketOptions = new DatagramCrusherSocketOptions();
    }

    public static DatagramCrusherBuilder builder() {
        return new DatagramCrusherBuilder();
    }

    public DatagramCrusherBuilder withLocalAddress(InetSocketAddress address) {
        this.localAddress = address;
        return this;
    }

    public DatagramCrusherBuilder withLocalAddress(String hostname, int port) {
        this.localAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    public DatagramCrusherBuilder withRemoteAddress(InetSocketAddress address) {
        this.remoteAddress = address;
        return this;
    }

    public DatagramCrusherBuilder withRemoteAddress(String hostname, int port) {
        this.remoteAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    public DatagramCrusherBuilder withReactor(NioReactor reactor) {
        this.reactor = reactor;
        return this;
    }

    public DatagramCrusherBuilder withBroadcast(boolean broadcast) {
        this.socketOptions.setBroadcast(broadcast);
        return this;
    }

    public DatagramCrusherBuilder withProtocolFamily(ProtocolFamily protocolFamily) {
        this.socketOptions.setProtocolFamily(protocolFamily);
        return this;
    }

    public DatagramCrusherBuilder withMaxIdleDurationMs(long maxIdleDurationMs) {
        this.maxIdleDurationMs = maxIdleDurationMs;
        return this;
    }

    public DatagramCrusher build() {
        if (localAddress == null) {
            throw new IllegalArgumentException("Local address is not set");
        }

        if (remoteAddress == null) {
            throw new IllegalArgumentException("Remote address is not set");
        }

        if (reactor == null) {
            throw new IllegalArgumentException("Context is not set");
        }

        return new DatagramCrusher(localAddress, remoteAddress, socketOptions.copy(), reactor,
            maxIdleDurationMs);
    }
}
