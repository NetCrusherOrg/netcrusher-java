package org.netcrusher.datagram;

import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.PassFilterFactory;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.filter.TransformFilterFactory;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.core.throttle.ThrottlerFactory;
import org.netcrusher.datagram.callback.DatagramClientCreation;
import org.netcrusher.datagram.callback.DatagramClientDeletion;

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;

/**
 * Builder for DatagramCrusher instance
 */
public final class DatagramCrusherBuilder {

    private final DatagramCrusherOptions options;

    private DatagramCrusherBuilder() {
        this.options = new DatagramCrusherOptions();
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
        this.options.setBindAddress(address);
        return this;
    }

    /**
     * Set local address for proxy (where to bind a listening socket)
     * @param hostname Host name or interface address
     * @param port Port number
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBindAddress(String hostname, int port) {
        return withBindAddress(new InetSocketAddress(hostname, port));
    }

    /**
     * Set remote address for proxy (where to connect)
     * @param address Inet address
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withConnectAddress(InetSocketAddress address) {
        this.options.setConnectAddress(address);
        return this;
    }

    /**
     * Set remote address for proxy (where to connect)
     * @param hostname Remote host name or IP address of remote host
     * @param port Port number
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withConnectAddress(String hostname, int port) {
        return withConnectAddress(new InetSocketAddress(hostname, port));
    }

    /**
     * Set reactor instance for this proxy
     * @param reactor Reactor
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withReactor(NioReactor reactor) {
        this.options.setReactor(reactor);
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
        this.options.getSocketOptions().setBroadcast(broadcast);
        return this;
    }

    /**
     * Set protocol family for both sockets
     * @param protocolFamily Protocol family
     * @see StandardProtocolFamily
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withProtocolFamily(ProtocolFamily protocolFamily) {
        this.options.getSocketOptions().setProtocolFamily(protocolFamily);
        return this;
    }

    /**
     * Set socket buffer size for receiving, If not set the default size will be used.
     * @param bufferSize Size in bytes
     * @see StandardSocketOptions#SO_RCVBUF
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withRcvBufferSize(int bufferSize) {
        this.options.getSocketOptions().setRcvBufferSize(bufferSize);
        return this;
    }

    /**
     * Set socket buffer size for sending, If not set the default size will be used.
     * @param bufferSize Size in bytes
     * @see StandardSocketOptions#SO_SNDBUF
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withSndBufferSize(int bufferSize) {
        this.options.getSocketOptions().setSndBufferSize(bufferSize);
        return this;
    }

    /**
     * Set how many buffer instances will be in queue between two sockets in a proxy pair
     * @param bufferCount Count of buffer
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBufferCount(int bufferCount) {
        this.options.getBufferOptions().setCount(bufferCount);
        return this;
    }

    /**
     * Set the size of each buffer in queue between two sockets in a proxy pair
     * @param bufferSize Size of buffer in bytes. Should not be less than the maximum size of datagram
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBufferSize(int bufferSize) {
        this.options.getBufferOptions().setSize(bufferSize);
        return this;
    }

    /**
     * Set buffer allocation method
     * @param direct Set true if ByteBuffer should be allocated as direct
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withBufferDirect(boolean direct) {
        this.options.getBufferOptions().setDirect(direct);
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) transform filter factory
     * @param filterFactory Filter factory
     * @return This builder instance to chain with other methods
     * @see TransformFilter
     */
    public DatagramCrusherBuilder withOutgoingTransformFilterFactory(TransformFilterFactory filterFactory) {
        this.options.setOutgoingTransformFilterFactory(filterFactory);
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) transform filter factory
     * @param filterFactory Filter factory
     * @return This builder instance to chain with other methods
     * @see TransformFilter
     */
    public DatagramCrusherBuilder withIncomingTransformFilterFactory(TransformFilterFactory filterFactory) {
        this.options.setIncomingTransformFilterFactory(filterFactory);
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) pass filter factory
     * @param filterFactory Filter factory
     * @return This builder instance to chain with other methods
     * @see PassFilter
     */
    public DatagramCrusherBuilder withOutgoingPassFilterFactory(PassFilterFactory filterFactory) {
        this.options.setOutgoingPassFilterFactory(filterFactory);
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) pass filter factory
     * @param filterFactory Filter factory
     * @return This builder instance to chain with other methods
     * @see PassFilter
     */
    public DatagramCrusherBuilder withIncomingPassFilterFactory(PassFilterFactory filterFactory) {
        this.options.setIncomingPassFilterFactory(filterFactory);
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) throttling factory
     * @param throttlerFactory Throttler factory
     * @return This builder instance to chain with other methods
     * @see Throttler
     */
    public DatagramCrusherBuilder withOutgoingThrottlerFactory(ThrottlerFactory throttlerFactory) {
        this.options.setOutgoingThrottlerFactory(throttlerFactory);
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) throttler. For datagram crusher incoming throttler works on
     * the total incoming packet stream not the individual channel streams.
     * @param throttler Throttler strategy
     * @return This builder instance to chain with other methods
     * @see Throttler
     */
    public DatagramCrusherBuilder withIncomingThrottler(Throttler throttler) {
        this.options.setIncomingThrottler(throttler);
        return this;
    }

    /**
     * Set a listener for a new proxy connection
     * @param creationListener Listener implementation
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withCreationListener(DatagramClientCreation creationListener) {
        this.options.setCreationListener(creationListener);
        return this;
    }

    /**
     * Set a listener for a proxy connection to be deleted
     * @param deletionListener Listener implementation
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withDeletionListener(DatagramClientDeletion deletionListener) {
        this.options.setDeletionListener(deletionListener);
        return this;
    }

    /**
     * Set listeners call method
     * @param deferredListeners Set true if listeners should be called from separate thread
     * @return This builder instance to chain with other methods
     */
    public DatagramCrusherBuilder withDeferredListeners(boolean deferredListeners) {
        this.options.setDeferredListeners(deferredListeners);
        return this;
    }

    /**
     * Builds a new DatagramCrusher instance
     * @return DatagramCrusher instance
     */
    public DatagramCrusher build() {
        return new DatagramCrusher(options);
    }

    /**
     * Builds a new DatagramCrusher instance and opens it for incoming packets
     * @return DatagramCrusher instance
     */
    public DatagramCrusher buildAndOpen() {
        DatagramCrusher crusher = build();
        crusher.open();
        return crusher;
    }

}
