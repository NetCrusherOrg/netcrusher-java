package org.netcrusher.datagram.bulk;

import java.io.Serializable;

public class DatagramBulkResult implements Serializable {

    private byte[] digest;

    private int count;

    private long bytes;

    private long elapsedMs;

    public byte[] getDigest() {
        return digest;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
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
        return String.format("%d bytes, %d datagrams, %d ms, md5=%s",
            bytes, count, elapsedMs, toHexString(digest));
    }

    private static String toHexString(byte[] bytes) {
        if (bytes != null) {
            StringBuilder hexString = new StringBuilder();

            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } else {
            return "null";
        }
    }
}
