package org.netcrusher.tcp;

import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.throttle.Throttler;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

class TcpQueue implements Serializable {

    private final Deque<Entry> reading;

    private final Deque<Entry> writing;

    private final Entry[] entryArray;

    private final ByteBuffer[] bufferArray;

    private final TransformFilter filter;

    private final Throttler throttler;

    private final InetSocketAddress clientAddress;

    public TcpQueue(InetSocketAddress clientAddress, TransformFilter filter, Throttler throttler,
                    int bufferCount, int bufferSize) {
        this.reading = new ArrayDeque<>(bufferCount);
        this.writing = new ArrayDeque<>(bufferCount);
        this.bufferArray = new ByteBuffer[bufferCount];
        this.entryArray = new Entry[bufferCount];
        this.filter = filter;
        this.throttler = throttler;
        this.clientAddress = clientAddress;

        for (int i = 0; i < bufferCount; i++) {
            this.writing.add(new Entry(bufferSize));
        }
    }

    public void clear() {
        writing.addAll(reading);
        reading.clear();
        writing.forEach((e) -> e.getBuffer().clear());
    }

    public int calculateReadingBytes() {
        int size = 0;

        for (Entry entry : reading) {
            size += entry.getBuffer().remaining();
        }

        Entry entryToSteal = writing.peekFirst();
        if (entryToSteal != null) {
            size += entryToSteal.getBuffer().position();
        }

        return size;
    }

    public int calculateWritingBytes() {
        int size = 0;

        for (Entry entry : writing) {
            size += entry.getBuffer().remaining();
        }

        return size;
    }

    public int countReady() {
        return reading.size();
    }

    public int countStage() {
        return writing.size();
    }

    public TcpQueueArray requestReading() {
        Entry entryToSteal = writing.peekFirst();
        if (entryToSteal != null && entryToSteal.getBuffer().position() > 0) {
            freeWriting();
        }

        final int size = reading.size();
        if (size == 0) {
            return TcpQueueArray.EMPTY;
        }

        reading.toArray(entryArray);
        for (int i = 0; i < size; i++) {
            bufferArray[i] = entryArray[i].getBuffer();
        }

        return new TcpQueueArray(bufferArray, 0, size);
    }

    public void cleanReading() {
        while (!reading.isEmpty()) {
            Entry entry = reading.getFirst();
            if (entry.getBuffer().hasRemaining()) {
                break;
            } else {
                freeReading();
            }
        }
    }

    private void freeReading() {
        Entry entry = reading.removeFirst();

        entry.getBuffer().clear();

        writing.addLast(entry);
    }

    public TcpQueueArray requestWriting() {
        final int size = writing.size();
        if (size == 0) {
            return TcpQueueArray.EMPTY;
        }

        writing.toArray(entryArray);
        for (int i = 0; i < size; i++) {
            bufferArray[i] = entryArray[i].getBuffer();
        }

        return new TcpQueueArray(bufferArray, 0, size);
    }

    public void cleanWriting() {
        while (!writing.isEmpty()) {
            Entry entry = writing.getFirst();

            if (entry.getBuffer().hasRemaining()) {
                break;
            } else {
                freeWriting();
            }
        }
    }

    private void freeWriting() {
        Entry entry = writing.removeFirst();

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

            reading.addLast(entry);
        } else {
            bb.clear();
            writing.add(entry);
        }
    }

    private static final class Entry implements Serializable {

        private final ByteBuffer buffer;

        private long scheduledNs;

        private Entry(int bufferSize) {
            this.buffer = ByteBuffer.allocate(bufferSize);
            this.scheduledNs = System.nanoTime();
        }

        public void schedule(long delayNs) {
            this.scheduledNs = System.nanoTime() + delayNs;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public long elapsedNs() {
            return Math.max(0, System.nanoTime() - scheduledNs);
        }

        public long elapsedMs() {
            return TimeUnit.NANOSECONDS.toMillis(elapsedNs());
        }

    }

}
