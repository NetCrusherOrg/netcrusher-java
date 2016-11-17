package org.netcrusher.tcp;

import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.filter.TransformFilterFactory;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.ThrottlerFactory;
import org.netcrusher.tcp.callback.TcpClientCreation;
import org.netcrusher.tcp.callback.TcpClientDeletion;

import java.net.InetSocketAddress;

public class TcpCrusherOptions {

    private static final int DEFAULT_BUFFER_COUNT = 64;

    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;

    private InetSocketAddress bindAddress;

    private InetSocketAddress connectAddress;

    private InetSocketAddress bindBeforeConnectAddress;

    private NioReactor reactor;

    private TcpCrusherSocketOptions socketOptions;

    private TcpClientCreation creationListener;

    private TcpClientDeletion deletionListener;

    private boolean deferredListeners;

    private TransformFilterFactory incomingTransformFilterFactory;

    private TransformFilterFactory outgoingTransformFilterFactory;

    private ThrottlerFactory incomingThrottlerFactory;

    private ThrottlerFactory outgoingThrottlerFactory;

    private BufferOptions bufferOptions;

    public TcpCrusherOptions() {
        this.socketOptions = new TcpCrusherSocketOptions();

        this.bufferOptions = new BufferOptions();
        this.bufferOptions.setCount(DEFAULT_BUFFER_COUNT);
        this.bufferOptions.setSize(DEFAULT_BUFFER_SIZE);
        this.bufferOptions.setDirect(true);

        this.deferredListeners = true;
    }

    public void validate() {
        if (bindAddress == null) {
            throw new IllegalArgumentException("Bind address is not set");
        }

        if (connectAddress == null) {
            throw new IllegalArgumentException("Connect address is not set");
        }

        if (reactor == null) {
            throw new IllegalArgumentException("Reactor is not set");
        }

        if (socketOptions == null) {
            throw new IllegalArgumentException("Socket options are not set");
        }

        if (bufferOptions == null) {
            throw new IllegalArgumentException("Buffer options are not set");
        }
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    public InetSocketAddress getConnectAddress() {
        return connectAddress;
    }

    public void setConnectAddress(InetSocketAddress connectAddress) {
        this.connectAddress = connectAddress;
    }

    public InetSocketAddress getBindBeforeConnectAddress() {
        return bindBeforeConnectAddress;
    }

    public void setBindBeforeConnectAddress(InetSocketAddress bindBeforeConnectAddress) {
        this.bindBeforeConnectAddress = bindBeforeConnectAddress;
    }

    public NioReactor getReactor() {
        return reactor;
    }

    public void setReactor(NioReactor reactor) {
        this.reactor = reactor;
    }

    public TcpCrusherSocketOptions getSocketOptions() {
        return socketOptions;
    }

    public void setSocketOptions(TcpCrusherSocketOptions socketOptions) {
        this.socketOptions = socketOptions;
    }

    public TcpClientCreation getCreationListener() {
        return creationListener;
    }

    public void setCreationListener(TcpClientCreation creationListener) {
        this.creationListener = creationListener;
    }

    public TcpClientDeletion getDeletionListener() {
        return deletionListener;
    }

    public void setDeletionListener(TcpClientDeletion deletionListener) {
        this.deletionListener = deletionListener;
    }

    public boolean isDeferredListeners() {
        return deferredListeners;
    }

    public void setDeferredListeners(boolean deferredListeners) {
        this.deferredListeners = deferredListeners;
    }

    public TransformFilterFactory getIncomingTransformFilterFactory() {
        return incomingTransformFilterFactory;
    }

    public void setIncomingTransformFilterFactory(TransformFilterFactory incomingTransformFilterFactory) {
        this.incomingTransformFilterFactory = incomingTransformFilterFactory;
    }

    public TransformFilterFactory getOutgoingTransformFilterFactory() {
        return outgoingTransformFilterFactory;
    }

    public void setOutgoingTransformFilterFactory(TransformFilterFactory outgoingTransformFilterFactory) {
        this.outgoingTransformFilterFactory = outgoingTransformFilterFactory;
    }

    public ThrottlerFactory getIncomingThrottlerFactory() {
        return incomingThrottlerFactory;
    }

    public void setIncomingThrottlerFactory(ThrottlerFactory incomingThrottlerFactory) {
        this.incomingThrottlerFactory = incomingThrottlerFactory;
    }

    public ThrottlerFactory getOutgoingThrottlerFactory() {
        return outgoingThrottlerFactory;
    }

    public void setOutgoingThrottlerFactory(ThrottlerFactory outgoingThrottlerFactory) {
        this.outgoingThrottlerFactory = outgoingThrottlerFactory;
    }

    public BufferOptions getBufferOptions() {
        return bufferOptions;
    }

    public void setBufferOptions(BufferOptions bufferOptions) {
        this.bufferOptions = bufferOptions;
    }

}
