package org.netcrusher.datagram;

import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.filter.PassFilterFactory;
import org.netcrusher.core.filter.TransformFilterFactory;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.core.throttle.ThrottlerFactory;
import org.netcrusher.datagram.callback.DatagramClientCreation;
import org.netcrusher.datagram.callback.DatagramClientDeletion;

import java.net.InetSocketAddress;

public class DatagramCrusherOptions {

    public static final int DEFAULT_BUFFER_COUNT = 1024;

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private InetSocketAddress bindAddress;

    private InetSocketAddress connectAddress;

    private NioReactor reactor;

    private DatagramCrusherSocketOptions socketOptions;

    private DatagramClientCreation creationListener;

    private DatagramClientDeletion deletionListener;

    private boolean deferredListeners;

    private TransformFilterFactory incomingTransformFilterFactory;

    private TransformFilterFactory outgoingTransformFilterFactory;

    private PassFilterFactory incomingPassFilterFactory;

    private PassFilterFactory outgoingPassFilterFactory;

    private Throttler incomingThrottler;

    private ThrottlerFactory outgoingThrottlerFactory;

    private BufferOptions bufferOptions;

    public DatagramCrusherOptions() {
        this.socketOptions = new DatagramCrusherSocketOptions();

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

    public NioReactor getReactor() {
        return reactor;
    }

    public void setReactor(NioReactor reactor) {
        this.reactor = reactor;
    }

    public DatagramCrusherSocketOptions getSocketOptions() {
        return socketOptions;
    }

    public void setSocketOptions(DatagramCrusherSocketOptions socketOptions) {
        this.socketOptions = socketOptions;
    }

    public DatagramClientCreation getCreationListener() {
        return creationListener;
    }

    public void setCreationListener(DatagramClientCreation creationListener) {
        this.creationListener = creationListener;
    }

    public DatagramClientDeletion getDeletionListener() {
        return deletionListener;
    }

    public void setDeletionListener(DatagramClientDeletion deletionListener) {
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

    public PassFilterFactory getIncomingPassFilterFactory() {
        return incomingPassFilterFactory;
    }

    public void setIncomingPassFilterFactory(PassFilterFactory incomingPassFilterFactory) {
        this.incomingPassFilterFactory = incomingPassFilterFactory;
    }

    public PassFilterFactory getOutgoingPassFilterFactory() {
        return outgoingPassFilterFactory;
    }

    public void setOutgoingPassFilterFactory(PassFilterFactory outgoingPassFilterFactory) {
        this.outgoingPassFilterFactory = outgoingPassFilterFactory;
    }

    public Throttler getIncomingThrottler() {
        return incomingThrottler;
    }

    public void setIncomingThrottler(Throttler incomingThrottler) {
        this.incomingThrottler = incomingThrottler;
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
