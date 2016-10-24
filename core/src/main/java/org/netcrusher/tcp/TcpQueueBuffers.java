package org.netcrusher.tcp;

import java.nio.ByteBuffer;

class TcpQueueBuffers {

    public static final TcpQueueBuffers EMPTY = new TcpQueueBuffers(null, -1, 0);

    private final ByteBuffer[] array;

    private final int offset;

    private final int count;

    public TcpQueueBuffers(ByteBuffer[] array, int offset, int count) {
        this.array = array;
        this.offset = offset;
        this.count = count;
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
}
