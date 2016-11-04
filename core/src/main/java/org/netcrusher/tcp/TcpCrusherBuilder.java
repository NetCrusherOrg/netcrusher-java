package org.netcrusher.tcp;

import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.filter.TransformFilterFactory;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.core.throttle.ThrottlerFactory;
import org.netcrusher.tcp.callback.TcpClientCreation;
import org.netcrusher.tcp.callback.TcpClientDeletion;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;

/**
 * Builder for TcpCrusher instance
 */
public final class TcpCrusherBuilder {

    private final TcpCrusherOptions options;

    private TcpCrusherBuilder() {
        this.options = new TcpCrusherOptions();
    }

    /**
     * Creates a new builder
     * @return A new builder instance
     */
    public static TcpCrusherBuilder builder() {
        return new TcpCrusherBuilder();
    }

    /**
     * Set local address for proxy (where to bind a listening socket)
     * @param address Inet address
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withBindAddress(InetSocketAddress address) {
        this.options.setBindAddress(address);
        return this;
    }

    /**
     * Set local address for proxy (where to bind a listening socket)
     * @param hostname Host name or interface address
     * @param port Port number
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withBindAddress(String hostname, int port) {
        return withBindAddress(new InetSocketAddress(hostname, port));
    }

    /**
     * Set remote address for proxy (where to connect)
     * @param address Inet address
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withConnectAddress(InetSocketAddress address) {
        this.options.setConnectAddress(address);
        return this;
    }

    /**
     * Set remote address for proxy (where to connect)
     * @param hostname Remote host name or IP address of remote host
     * @param port Port number
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withConnectAddress(String hostname, int port) {
        return withConnectAddress(new InetSocketAddress(hostname, port));
    }

    /**
     * Set reactor instance for this proxy
     * @param reactor Reactor
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withReactor(NioReactor reactor) {
        this.options.setReactor(reactor);
        return this;
    }

    /**
     * Set a listener for a new proxy connection
     * @param creationListener Listener implementation
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withCreationListener(TcpClientCreation creationListener) {
        this.options.setCreationListener(creationListener);
        return this;
    }

    /**
     * Set a listener for a proxy connection to be deleted
     * @param deletionListener Listener implementation
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withDeletionListener(TcpClientDeletion deletionListener) {
        this.options.setDeletionListener(deletionListener);
        return this;
    }

    /**
     * Set a backlog size for a listening socket. If not set the default backlog size will be used
     * @param backlog Backlog size
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withBacklog(int backlog) {
        this.options.getSocketOptions().setBacklog(backlog);
        return this;
    }

    /**
     * Set whether or not both sockets would use SO_KEEPALIVE feature
     * @param keepAlive SO_KEEPALIVE flag value
     * @see StandardSocketOptions#SO_KEEPALIVE
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withKeepAlive(boolean keepAlive) {
        this.options.getSocketOptions().setKeepAlive(keepAlive);
        return this;
    }

    /**
     * Set whether or not both sockets would use TCP_NODELAY feature
     * @param tcpNoDelay TCP_NODELAY flag value
     * @see StandardSocketOptions#TCP_NODELAY
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withTcpNoDelay(boolean tcpNoDelay) {
        this.options.getSocketOptions().setTcpNoDelay(tcpNoDelay);
        return this;
    }

    /**
     * Set socket buffer size for receiving, If not set the default size will be used.
     * @param bufferSize Size in bytes
     * @see StandardSocketOptions#SO_RCVBUF
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withRcvBufferSize(int bufferSize) {
        this.options.getSocketOptions().setRcvBufferSize(bufferSize);
        return this;
    }

    /**
     * Set socket buffer size for sending, If not set the default size will be used.
     * @param bufferSize Size in bytes
     * @see StandardSocketOptions#SO_SNDBUF
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withSndBufferSize(int bufferSize) {
        this.options.getSocketOptions().setSndBufferSize(bufferSize);
        return this;
    }

    /**
     * Set linger timeout in millisecond
     * @param timeoutMs Timeout in milliseconds
     * @see StandardSocketOptions#SO_LINGER
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withLingerMs(int timeoutMs) {
        this.options.getSocketOptions().setLingerMs(timeoutMs);
        return this;
    }

    /**
     * Connection timeout for remote connection. If set to 0 the timeout will be not timeout at all
     * @param timeoutMs Timeout in milliseconds
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withConnectionTimeoutMs(long timeoutMs) {
        this.options.getSocketOptions().setConnectionTimeoutMs(timeoutMs);
        return this;
    }

    /**
     * Set how many buffer instances will be in queue between two sockets in a proxy pair
     * @param bufferCount Count of buffer
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withBufferCount(int bufferCount) {
        this.options.getBufferOptions().setCount(bufferCount);
        return this;
    }

    /**
     * Set the size of each buffer in queue between two sockets in a proxy pair
     * @param bufferSize Size of buffer in bytes
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withBufferSize(int bufferSize) {
        this.options.getBufferOptions().setSize(bufferSize);
        return this;
    }

    /**
     * Set buffer allocation method
     * @param direct Set true if ByteBuffer should be allocated as direct
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withBufferDirect(boolean direct) {
        this.options.getBufferOptions().setDirect(direct);
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) transform filter factory
     * @param filterFactory Filter factory
     * @return This builder instance to chain with other methods
     * @see TransformFilter
     */
    public TcpCrusherBuilder withOutgoingTransformFilterFactory(TransformFilterFactory filterFactory) {
        this.options.setOutgoingTransformFilterFactory(filterFactory);
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) transform filter factory
     * @param filterFactory Filter factory
     * @return This builder instance to chain with other methods
     * @see TransformFilter
     */
    public TcpCrusherBuilder withIncomingTransformFilterFactory(TransformFilterFactory filterFactory) {
        this.options.setIncomingTransformFilterFactory(filterFactory);
        return this;
    }

    /**
     * Set outgoing (from the inner to the outer) throttler factory
     * @param throttlerFactory Throttler factory
     * @return This builder instance to chain with other methods
     * @see Throttler
     */
    public TcpCrusherBuilder withOutgoingThrottlerFactory(ThrottlerFactory throttlerFactory) {
        this.options.setOutgoingThrottlerFactory(throttlerFactory);
        return this;
    }

    /**
     * Set incoming (from the outer to the inner) throttler factory
     * @param throttlerFactory Throttler factory
     * @return This builder instance to chain with other methods
     * @see Throttler
     */
    public TcpCrusherBuilder withIncomingThrottlerFactory(ThrottlerFactory throttlerFactory) {
        this.options.setIncomingThrottlerFactory(throttlerFactory);
        return this;
    }

    /**
     * Set listeners call method
     * @param deferredListeners Set true if listeners should be called from separate thread
     * @return This builder instance to chain with other methods
     */
    public TcpCrusherBuilder withDeferredListeners(boolean deferredListeners) {
        this.options.setDeferredListeners(deferredListeners);
        return this;
    }

    /**
     * Builds a new TcpCrusher instance
     * @return TcpCrusher instance
     */
    public TcpCrusher build() {
        return new TcpCrusher(options);
    }

    /**
     * Builds a new TcpCrusher instance and opens it for incoming connections
     * @return TcpCrusher instance
     */
    public TcpCrusher buildAndOpen() {
        TcpCrusher crusher = build();
        crusher.open();
        return crusher;
    }

}
