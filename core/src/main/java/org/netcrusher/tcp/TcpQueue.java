package org.netcrusher.tcp;

import org.netcrusher.filter.ByteBufferFilter;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public class TcpQueue implements Serializable {

    private final Deque<ByteBuffer> ready;

    private final Deque<ByteBuffer> stage;

    private final ByteBuffer[] array;

    private final ByteBufferFilter[] filters;

    public TcpQueue(ByteBufferFilter[] filters, int bufferCount, int bufferSize) {
        this.ready = new ArrayDeque<>(bufferCount);
        this.stage = new ArrayDeque<>(bufferCount);
        this.array = new ByteBuffer[bufferCount];
        this.filters = filters;

        for (int i = 0; i < bufferCount; i++) {
            ByteBuffer bb = ByteBuffer.allocate(bufferSize);
            this.stage.add(bb);
        }
    }

    public void clear() {
        stage.addAll(ready);
        ready.clear();
        stage.forEach(ByteBuffer::clear);
    }

    public int calculateReadyBytes() {
        int size = 0;

        for (ByteBuffer bb : ready) {
            size += bb.remaining();
        }

        ByteBuffer bbToSteal = stage.peekFirst();
        if (bbToSteal != null) {
            size += bbToSteal.position();
        }

        return size;
    }

    public int calculateStageBytes() {
        int size = 0;

        for (ByteBuffer bb : stage) {
            size += bb.remaining();
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
        ByteBuffer bbToSteal = stage.peekFirst();
        if (bbToSteal != null && bbToSteal.position() > 0) {
            steal();
        }

        ready.toArray(array);
        return ready.size();
    }

    public void cleanReady() {
        while (!ready.isEmpty()) {
            ByteBuffer bb = ready.getFirst();
            if (bb.hasRemaining()) {
                break;
            } else {
                donate();
            }
        }
    }

    public int requestStage() {
        stage.toArray(array);
        return stage.size();
    }

    public void cleanStage() {
        while (!stage.isEmpty()) {
            ByteBuffer bb = stage.getFirst();
            if (bb.hasRemaining()) {
                break;
            } else {
                steal();
            }
        }
    }

    public ByteBuffer[] getArray() {
        return array;
    }

    private void steal() {
        ByteBuffer bb = stage.removeFirst();

        bb.flip();

        filter(bb);

        if (bb.hasRemaining()) {
            ready.addLast(bb);
        } else {
            bb.clear();
            stage.add(bb);
        }
    }

    private void donate() {
        ByteBuffer bb = ready.removeFirst();

        bb.clear();

        stage.addLast(bb);
    }

    private void filter(ByteBuffer bb) {
        if (filters != null) {
            for (ByteBufferFilter filter : filters) {
                filter.filter(bb);
            }
        }
    }

}
