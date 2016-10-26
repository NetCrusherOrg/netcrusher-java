package org.netcrusher.core.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;

public class BufferOptions implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferOptions.class);

    private int count;

    private int size;

    private boolean direct;

    public BufferOptions copy() {
        BufferOptions copy = new BufferOptions();

        copy.count = this.count;
        copy.size = this.size;
        copy.direct = this.direct;

        return copy;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    public void checkTcpSocket(Socket socket) throws SocketException {
        final long sizeTotal = count * size;

        if (sizeTotal < socket.getReceiveBufferSize()) {
            LOGGER.warn("Total buffer size {} is less than TCP socket SO_RCVBUF buffer size {}. Increase buffer size",
                sizeTotal, socket.getReceiveBufferSize());
        }

        if (sizeTotal < socket.getSendBufferSize()) {
            LOGGER.warn("Total buffer size {} is less than TCP socket SO_SNDBUF buffer size {}. Increase buffer size",
                sizeTotal, socket.getSendBufferSize());
        }
    }

    public void checkDatagramSocket(DatagramSocket socket) throws SocketException {
        final long sizeTotal = count * size;

        if (sizeTotal < socket.getReceiveBufferSize()) {
            LOGGER.warn("Total buffer size {} is less than UDP socket SO_RCVBUF buffer size {}. Increase buffer size",
                sizeTotal, socket.getReceiveBufferSize());
        }

        if (sizeTotal < socket.getSendBufferSize()) {
            LOGGER.warn("Total buffer size {} is less than UDP socket SO_SNDBUF buffer size {}. Increase buffer size",
                sizeTotal, socket.getSendBufferSize());
        }
    }

}
