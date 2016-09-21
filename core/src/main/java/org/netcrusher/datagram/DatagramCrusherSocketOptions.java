package org.netcrusher.datagram;

import java.io.IOException;
import java.io.Serializable;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;

public class DatagramCrusherSocketOptions implements Serializable {

    private int rcvBufferSize;

    private int sndBufferSize;

    private boolean broadcast;

    private ProtocolFamily protocolFamily;

    public DatagramCrusherSocketOptions() {
        this.rcvBufferSize = 0;
        this.sndBufferSize = 0;
        this.broadcast = false;
        this.protocolFamily = StandardProtocolFamily.INET;
    }

    public DatagramCrusherSocketOptions copy() {
        DatagramCrusherSocketOptions copy = new DatagramCrusherSocketOptions();

        copy.rcvBufferSize = this.rcvBufferSize;
        copy.sndBufferSize = this.sndBufferSize;
        copy.broadcast = this.broadcast;
        copy.protocolFamily = this.protocolFamily;

        return copy;
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

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public ProtocolFamily getProtocolFamily() {
        return protocolFamily;
    }

    public void setProtocolFamily(ProtocolFamily protocolFamily) {
        this.protocolFamily = protocolFamily;
    }

    protected void setupSocketChannel(DatagramChannel datagramChannel) throws IOException {
        datagramChannel.setOption(StandardSocketOptions.SO_BROADCAST, isBroadcast());

        if (getRcvBufferSize() > 0) {
            datagramChannel.setOption(StandardSocketOptions.SO_RCVBUF, getRcvBufferSize());
        }

        if (getSndBufferSize() > 0) {
            datagramChannel.setOption(StandardSocketOptions.SO_SNDBUF, getSndBufferSize());
        }
    }

}
