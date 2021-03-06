package org.netcrusher.tcp.bulk;

import org.netcrusher.core.nio.NioUtils;

import java.io.Serializable;

public class TcpBulkResult implements Serializable {

    private byte[] digest;

    private long bytes;

    private long elapsedMs;

    public byte[] getDigest() {
        return digest;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest;
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    @Override
    public String toString() {
        return String.format("%d bytes, %d ms, md5=%s",
            bytes, elapsedMs, NioUtils.toHexString(digest));
    }

}
