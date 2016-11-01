package org.netcrusher.tcp;

import java.nio.ByteBuffer;

class TcpQueueBuffers {

    public static final TcpQueueBuffers EMPTY = new TcpQueueBuffers(null, -1, 0, 0);

    private final ByteBuffer[] array;

    private final int offset;

    private final int count;

    private final long delayNs;

    TcpQueueBuffers(ByteBuffer[] array, int offset, int count, long delayNs) {
        this.array = array;
        this.offset = offset;
        this.count = count;
        this.delayNs = delayNs;
    }

    TcpQueueBuffers(ByteBuffer[] array, int offset, int count) {
        this(array, offset, count, 0);
    }

    public ByteBuffer[] getArray() {
        return array;
    }

    public int getOffset() {
        return offset;
    }

    public int getCount() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public long getDelayNs() {
        return delayNs;
    }
}
