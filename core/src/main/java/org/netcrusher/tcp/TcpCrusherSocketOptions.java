package org.netcrusher.tcp;

import java.io.IOException;
import java.io.Serializable;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

public class TcpCrusherSocketOptions implements Serializable {

    private int backlog;

    private int rcvBufferSize;

    private int sndBufferSize;

    private long connectionTimeoutMs;

    private boolean tcpNoDelay;

    private boolean keepAlive;

    public TcpCrusherSocketOptions() {
        this.backlog = 0;
        this.rcvBufferSize = 0;
        this.sndBufferSize = 0;
        this.connectionTimeoutMs = 3000;
        this.tcpNoDelay = true;
        this.keepAlive = true;
    }

    public TcpCrusherSocketOptions copy() {
        TcpCrusherSocketOptions copy = new TcpCrusherSocketOptions();

        copy.backlog = this.backlog;
        copy.rcvBufferSize = this.rcvBufferSize;
        copy.sndBufferSize = this.sndBufferSize;
        copy.connectionTimeoutMs = this.connectionTimeoutMs;
        copy.tcpNoDelay = this.tcpNoDelay;
        copy.keepAlive = this.keepAlive;

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

    void setupSocketChannel(SocketChannel socketChannel) throws IOException {
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);

        if (rcvBufferSize > 0) {
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, rcvBufferSize);
        }

        if (sndBufferSize > 0) {
            socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, sndBufferSize);
        }
    }
}
