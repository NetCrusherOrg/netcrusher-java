package org.netcrusher.tcp;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Circular queue for ByteBuffer cells with restricted capacity
 */
public class TcpTransferQueue implements Serializable {

    private final ByteBuffer[] buffers;

    private final boolean[] reads;

    private final int capacity;

    private int offset;

    private int size;

    private long pending;

    public TcpTransferQueue(int bufferCount, int bufferSize) {
        if (bufferCount <= 1) {
            throw new IllegalArgumentException("Buffer capacity is invalid");
        }
        if (bufferSize < 16) {
            throw new IllegalArgumentException("Buffer size is invalid");
        }

        this.capacity = bufferCount;

        this.buffers = new ByteBuffer[capacity];
        for (int i = 0; i < capacity; i++) {
            this.buffers[i] = ByteBuffer.allocateDirect(bufferSize);
        }

        this.reads = new boolean[capacity];

        clear();
    }

    public void clear() {
        this.offset = 0;
        this.size = 0;
        this.pending = 0;
    }

    public int size() {
        return size;
    }

    public long pending() {
        return pending;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == capacity;
    }

    private int getHeadIndex() {
        return cycle(offset + size - 1);
    }

    public ByteBuffer requestHeadBuffer() {
        if (!isEmpty() && !reads[getHeadIndex()]) {
            return buffers[getHeadIndex()];
        } else if (isFull()) {
            return null;
        } else {
            size = size + 1;

            int index = getHeadIndex();
            reads[index] = false;

            ByteBuffer result = buffers[index];
            result.clear();

            return result;
        }
    }

    public void releaseHeadBuffer() {
        if (isEmpty()) {
            throw new IllegalStateException("Queue is empty");
        }

        int index = getHeadIndex();
        if (reads[index]) {
            throw new IllegalStateException("Head buffer is already available to read");
        } else {
            reads[index] = true;
            buffers[index].flip();
        }
    }

    public ByteBuffer requestTailBuffer() {
        if (isEmpty()) {
            return null;
        } else {
            ByteBuffer result = buffers[offset];

            if (!reads[offset]) {
                if (size > 1) {
                    throw new IllegalStateException("Buffer is not readable nor the last one");
                }
                if (result.position() > 0) {
                    reads[offset] = true;
                    result.flip();
                } else {
                    return null;
                }
            }

            return result;
        }
    }

    public void releaseTailBuffer() {
        if (isEmpty()) {
            throw new IllegalStateException("Queue is empty");
        } else {
            size = size - 1;
            offset = cycle(offset + 1);
        }
    }

    public void changePending(long delta) {
        this.pending += delta;
    }

    private int cycle(int offset) {
        if (offset < capacity) {
            return offset;
        } else {
            return offset % capacity;
        }
    }
}
