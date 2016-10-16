package org.netcrusher.datagram;

import org.netcrusher.core.NioReactor;
import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.TransformFilter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;

/**
 * Builder for DatagramCrusher instance
 */
public final class DatagramCrusherBuilder {

    private InetSocketAddress bindAddress;

    private InetSocketAddress connectAddress;

    private NioReactor reactor;

    private DatagramCrusherSocketOptions socketOptions;

    private TransformFilter incomingTransformFilter;

    private TransformFilter outgoingTransformFilter;

    private PassFilter incomingPassFilter;

    private PassFilter outgoingPassFilter;

    private long maxIdleDurationMs;

    private DatagramCrusherBuilder() {
        this.socketOptions = new DatagramCrusherSocketOptions();
    }

    /**
     * Create a new builder
     * @return Builder instance
     */
    public static DatagramCrusherBuilder builder() {
        return new DatagramCrusherBuilder();
    }

    /**
     * Set local address for proxy (where to bind a listening socket)
     * @param address Inet address
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBindAddress(InetSocketAddress address) {
        this.bindAddress = address;
        return this;
    }

    /**
     * Set local address for proxy (where to bind a listening socket)
     * @param hostname Host name or interface address
     * @param port Port number
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBindAddress(String hostname, int port) {
        this.bindAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    /**
     * Set remote address for proxy (where to connect)
     * @param address Inet address
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withConnectAddress(InetSocketAddress address) {
        this.connectAddress = address;
        return this;
    }

    /**
     * Set remote address for proxy (where to connect)
     * @param hostname Remote host name or IP address of remote host
     * @param port Port number
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withConnectAddress(String hostname, int port) {
        this.connectAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    /**
     * Set reactor instance for this proxy
     * @param reactor Reactor
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withReactor(NioReactor reactor) {
        this.reactor = reactor;
        return this;
    }

    /**
     * Set broadcast flag for both sockets
     * @param broadcast Broadcast flag
     * @see StandardSocketOptions#SO_BROADCAST
     * @see StandardProtocolFamily#INET
     * @see StandardProtocolFamily#INET6
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBroadcast(boolean broadcast) {
        this.socketOptions.setBroadcast(broadcast);
        return this;
    }

    /**
     * Set protocol family for both sockets
     * @param protocolFamily Protocol family
     * @see StandardProtocolFamily
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withProtocolFamily(ProtocolFamily protocolFamily) {
        this.socketOptions.setProtocolFamily(protocolFamily);
        return this;
    }

    /**
     * Set a maximum idle duration for outgoing socket channel. If no any activity is registered on the channel for
     * the specified period - the channel will be closed. If value is not set all channels will exist until
     * the crusher instance remains open.
     * @param maxIdleDurationMs Maximum idle duration in milliseconds
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withMaxIdleDurationMs(long maxIdleDurationMs) {
        this.maxIdleDurationMs = maxIdleDurationMs;
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) transform filter
     * @param filter Filter instance
     * @return This builder instance to chain with other methods
     * @see TransformFilter
     */
    public DatagramCrusherBuilder withOutgoingTransformFilter(TransformFilter filter) {
        this.outgoingTransformFilter = filter;
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) transform filter
     * @param filter Filter instance
     * @return This builder instance to chain with other methods
     * @see TransformFilter
     */
    public DatagramCrusherBuilder withIncomingTransformFilter(TransformFilter filter) {
        this.incomingTransformFilter = filter;
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) pass filter
     * @param filter Filter instance
     * @return This builder instance to chain with other methods
     * @see PassFilter
     */
    public DatagramCrusherBuilder withOutgoingPassFilter(PassFilter filter) {
        this.outgoingPassFilter = filter;
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) pass filter
     * @param filter Filter instance
     * @return This builder instance to chain with other methods
     * @see PassFilter
     */
    public DatagramCrusherBuilder withIncomingPassFilter(PassFilter filter) {
        this.incomingPassFilter = filter;
        return this;
    }

    /**
     * Builds a new DatagramCrusher instance
     * @return DatagramCrusher instance
     */
    public DatagramCrusher build() {
        if (bindAddress == null) {
            throw new IllegalArgumentException("Bind address is not set");
        }

        if (connectAddress == null) {
            throw new IllegalArgumentException("Connect address is not set");
        }

        if (reactor == null) {
            throw new IllegalArgumentException("Reactor is not set");
        }

        DatagramFilters filters = new DatagramFilters(
            incomingTransformFilter,
            outgoingTransformFilter,
            incomingPassFilter,
            outgoingPassFilter
        );

        return new DatagramCrusher(
            reactor,
            bindAddress,
            connectAddress,
            socketOptions.copy(),
            filters,
            maxIdleDurationMs);
    }

    /**
     * Builds a new DatagramCrusher instance and opens it for incoming packets
     * @return DatagramCrusher instance
     * @throws IOException Raises if opening fails
     */
    public DatagramCrusher buildAndOpen() throws IOException {
        DatagramCrusher crusher = build();
        crusher.open();
        return crusher;
    }

}
