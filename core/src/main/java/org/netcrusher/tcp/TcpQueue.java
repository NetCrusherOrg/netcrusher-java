package org.netcrusher.tcp;

import org.netcrusher.core.filter.TransformFilter;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

public class TcpQueue implements Serializable {

    private final Deque<Entry> ready;

    private final Deque<Entry> stage;

    private final Entry[] entryArray;

    private final ByteBuffer[] bufferArray;

    private final TransformFilter filter;

    private final InetSocketAddress clientAddress;

    public TcpQueue(TransformFilter filter, InetSocketAddress clientAddress, int bufferCount, int bufferSize) {
        this.ready = new ArrayDeque<>(bufferCount);
        this.stage = new ArrayDeque<>(bufferCount);
        this.bufferArray = new ByteBuffer[bufferCount];
        this.entryArray = new Entry[bufferCount];
        this.filter = filter;
        this.clientAddress = clientAddress;

        for (int i = 0; i < bufferCount; i++) {
            this.stage.add(new Entry(bufferSize));
        }
    }

    public void clear() {
        stage.addAll(ready);
        ready.clear();
        stage.forEach((e) -> e.getBuffer().clear());
    }

    public int calculateReadyBytes() {
        int size = 0;

        for (Entry entry : ready) {
            size += entry.getBuffer().remaining();
        }

        Entry entryToSteal = stage.peekFirst();
        if (entryToSteal != null) {
            size += entryToSteal.getBuffer().position();
        }

        return size;
    }

    public int calculateStageBytes() {
        int size = 0;

        for (Entry entry : stage) {
            size += entry.getBuffer().remaining();
        }

        return size;
    }

    public int countReady() {
        return ready.size();
    }

    public int countStage() {
        return stage.size();
    }

    public int requestReady() {
        Entry entryToSteal = stage.peekFirst();
        if (entryToSteal != null && entryToSteal.getBuffer().position() > 0) {
            steal();
        }

        final int size = ready.size();

        ready.toArray(entryArray);
        for (int i = 0; i < size; i++) {
            bufferArray[i] = entryArray[i].getBuffer();
        }

        return size;
    }

    public void cleanReady() {
        while (!ready.isEmpty()) {
            Entry entry = ready.getFirst();
            if (entry.getBuffer().hasRemaining()) {
                break;
            } else {
                donate();
            }
        }
    }

    public int requestStage() {
        final int size = stage.size();

        stage.toArray(entryArray);
        for (int i = 0; i < size; i++) {
            bufferArray[i] = entryArray[i].getBuffer();
        }

        return size;
    }

    public void cleanStage() {
        while (!stage.isEmpty()) {
            Entry entry = stage.getFirst();

            if (entry.getBuffer().position() > 0) {
                entry.schedule();
            }

            if (entry.getBuffer().hasRemaining()) {
                break;
            } else {
                steal();
            }
        }
    }

    public ByteBuffer[] getBufferArray() {
        return bufferArray;
    }

    private void steal() {
        Entry entry = stage.removeFirst();

        entry.getBuffer().flip();

        if (filter != null) {
            filter.transform(clientAddress, entry.getBuffer());
        }

        if (entry.getBuffer().hasRemaining()) {
            ready.addLast(entry);
        } else {
            entry.getBuffer().clear();
            stage.add(entry);
        }
    }

    private void donate() {
        Entry entry = ready.removeFirst();

        entry.getBuffer().clear();

        stage.addLast(entry);
    }

    private static final class Entry implements Serializable {

        private final ByteBuffer buffer;

        private long scheduledNs;

        private Entry(int bufferSize) {
            this.buffer = ByteBuffer.allocate(bufferSize);
            this.scheduledNs = System.nanoTime();
        }

        public void schedule() {
            this.scheduledNs = System.nanoTime();
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
