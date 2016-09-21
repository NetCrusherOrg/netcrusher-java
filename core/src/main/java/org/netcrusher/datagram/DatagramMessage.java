package org.netcrusher.datagram;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class DatagramMessage implements Serializable {

    private final InetSocketAddress address;

    private final ByteBuffer buffer;

    public DatagramMessage(InetSocketAddress address, ByteBuffer buffer) {
        this.address = address;
        this.buffer = buffer;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }
}
