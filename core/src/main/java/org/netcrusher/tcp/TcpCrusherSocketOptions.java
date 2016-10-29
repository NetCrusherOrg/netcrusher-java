package org.netcrusher.tcp;

import java.io.IOException;
import java.io.Serializable;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

public class TcpCrusherSocketOptions implements Serializable {

    public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 5000;

    public static final int DEFAULT_BACKLOG = 10;

    private int backlog;

    private int rcvBufferSize;

    private int sndBufferSize;

    private long connectionTimeoutMs;

    private boolean tcpNoDelay;

    private boolean keepAlive;

    private int lingerMs;

    public TcpCrusherSocketOptions() {
        this.backlog = DEFAULT_BACKLOG;
        this.rcvBufferSize = 0;
        this.sndBufferSize = 0;
        this.connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;
        this.tcpNoDelay = true;
        this.keepAlive = true;
        this.lingerMs = -1;
    }

    public TcpCrusherSocketOptions copy() {
        TcpCrusherSocketOptions copy = new TcpCrusherSocketOptions();

        copy.backlog = this.backlog;
        copy.rcvBufferSize = this.rcvBufferSize;
        copy.sndBufferSize = this.sndBufferSize;
        copy.connectionTimeoutMs = this.connectionTimeoutMs;
        copy.tcpNoDelay = this.tcpNoDelay;
        copy.keepAlive = this.keepAlive;
        copy.lingerMs = this.lingerMs;

        return copy;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getRcvBufferSize() {
        return rcvBufferSize;
    }

    public void setRcvBufferSize(int rcvBufferSize) {
        this.rcvBufferSize = rcvBufferSize;
    }

    public int getSndBufferSize() {
        return sndBufferSize;
    }

    public void setSndBufferSize(int sndBufferSize) {
        this.sndBufferSize = sndBufferSize;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getLingerMs() {
        return lingerMs;
    }

    public void setLingerMs(int lingerMs) {
        this.lingerMs = lingerMs;
    }

    void setupSocketChannel(SocketChannel socketChannel) throws IOException {
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);

        if (rcvBufferSize > 0) {
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, rcvBufferSize);
        }

        if (sndBufferSize > 0) {
            socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, sndBufferSize);
        }

        if (lingerMs >= 0) {
            socketChannel.setOption(StandardSocketOptions.SO_LINGER, lingerMs);
        }
    }
}
