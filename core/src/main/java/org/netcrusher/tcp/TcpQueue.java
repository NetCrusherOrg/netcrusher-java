package org.netcrusher.tcp;

import org.netcrusher.core.buffer.BufferOptions;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.nio.NioUtils;
import org.netcrusher.core.throttle.Throttler;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

class TcpQueue implements Serializable {

    private final Queue<BufferEntry> readable;

    private final Queue<BufferEntry> writable;

    private final BufferEntry[] entryArray;

    private final ByteBuffer[] bufferArray;

    private final TransformFilter filter;

    private final Throttler throttler;

    private final InetSocketAddress clientAddress;

    TcpQueue(
            InetSocketAddress clientAddress,
            TransformFilter filter,
            Throttler throttler,
            BufferOptions bufferOptions)
    {
        final int count = bufferOptions.getCount();

        this.readable = new ArrayDeque<>(count);
        this.writable = new ArrayDeque<>(count);

        this.bufferArray = new ByteBuffer[count];
        this.entryArray = new BufferEntry[count];

        this.filter = filter;
        this.throttler = throttler;
        this.clientAddress = clientAddress;

        for (int i = 0; i < count; i++) {
            this.writable.add(new BufferEntry(bufferOptions.getSize(), bufferOptions.isDirect()));
        }
    }

    public void reset() {
        writable.addAll(readable);
        readable.clear();
        writable.forEach((e) -> e.getBuffer().clear());
    }

    public boolean hasReadable() {
        BufferEntry readableEntry = readable.peek();
        if (readableEntry != null) {
            if (readableEntry.getBuffer().hasRemaining()) {
                return true;
            } else {
                throw new IllegalStateException("Illegal queue state. Possibly no release() call after request()");
            }
        }

        BufferEntry writableEntry = writable.peek();
        if (writableEntry != null) {
            if (writableEntry.getBuffer().position() > 0) {
                return true;
            }
        }

        return false;
    }

    public long calculateReadableBytes() {
        long size = 0;

        for (BufferEntry readableEntry : readable) {
            size += readableEntry.getBuffer().remaining();
        }

        BufferEntry writableEntry = writable.peek();
        if (writableEntry != null) {
            size += writableEntry.getBuffer().position();
        }

        return size;
    }

    public TcpQueueBuffers requestReadableBuffers() {
        BufferEntry entryToSteal = writable.peek();
        if (entryToSteal != null && entryToSteal.getBuffer().position() > 0) {
            freeWritableBuffer();
        }

        final int size = readable.size();
        if (size == 0) {
            return TcpQueueBuffers.EMPTY;
        }

        long nowNs = System.nanoTime();

        readable.toArray(entryArray);
        for (int i = 0; i < size; i++) {
            BufferEntry entry = entryArray[i];

            long delayNs = entry.scheduledNs - nowNs;
            if (delayNs > 0) {
                return new TcpQueueBuffers(bufferArray, 0, i, delayNs);
            } else {
                bufferArray[i] = entry.getBuffer();
            }
        }

        return new TcpQueueBuffers(bufferArray, 0, size);
    }

    public void releaseReadableBuffers() {
        while (!readable.isEmpty()) {
            BufferEntry entry = readable.element();
            if (entry.getBuffer().hasRemaining()) {
                break;
            } else {
                freeReadableBuffer();
            }
        }
    }

    private void freeReadableBuffer() {
        BufferEntry entry = readable.remove();

        entry.getBuffer().clear();

        writable.add(entry);
    }

    public boolean hasWritable() {
        BufferEntry entry = writable.peek();
        if (entry != null) {
            if (entry.getBuffer().hasRemaining()) {
                return true;
            } else {
                throw new IllegalStateException("Illegal queue state. Possibly no release() call after request()");
            }
        }

        return false;
    }

    public long calculateWritableBytes() {
        long size = 0;

        for (BufferEntry entry : writable) {
            size += entry.getBuffer().remaining();
        }

        return size;
    }

    public TcpQueueBuffers requestWritableBuffers() {
        final int size = writable.size();
        if (size == 0) {
            return TcpQueueBuffers.EMPTY;
        }

        writable.toArray(entryArray);
        for (int i = 0; i < size; i++) {
            BufferEntry entry = entryArray[i];
            bufferArray[i] = entry.getBuffer();
        }

        return new TcpQueueBuffers(bufferArray, 0, size);
    }

    public void releaseWritableBuffers() {
        while (!writable.isEmpty()) {
            BufferEntry entry = writable.element();
            if (entry.getBuffer().hasRemaining()) {
                break;
            } else {
                freeWritableBuffer();
            }
        }
    }

    private void freeWritableBuffer() {
        BufferEntry entry = writable.remove();

        ByteBuffer bb = entry.getBuffer();
        bb.flip();

        if (filter != null) {
            filter.transform(clientAddress, bb);
        }

        if (bb.hasRemaining()) {
            final long delayNs;
            if (throttler != null) {
                delayNs = throttler.calculateDelayNs(clientAddress, bb);
            } else {
                delayNs = Throttler.NO_DELAY_NS;
            }

            entry.schedule(delayNs);

            readable.add(entry);
        } else {
            bb.clear();
            writable.add(entry);
        }
    }

    private static final class BufferEntry implements Serializable {

        private final ByteBuffer buffer;

        private long scheduledNs;

        private BufferEntry(int capacity, boolean direct) {
            this.buffer = NioUtils.allocaleByteBuffer(capacity, direct);
            this.scheduledNs = System.nanoTime();
        }

        public void schedule(long delayNs) {
            this.scheduledNs = System.nanoTime() + delayNs;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

    }

}
