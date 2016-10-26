package org.netcrusher.datagram;

import org.netcrusher.core.buffer.BufferOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

class DatagramQueue implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramQueue.class);

    private final Deque<BufferEntry> entries;

    private final Queue<BufferEntry> pending;

    public DatagramQueue(BufferOptions bufferOptions) {
        this.entries = new ArrayDeque<>(bufferOptions.getCount());
        this.pending = new ArrayDeque<>(bufferOptions.getCount());

        for (int i = 0, limit = bufferOptions.getCount(); i < limit; i++) {
            this.pending.add(new BufferEntry(bufferOptions.getSize(), bufferOptions.isDirect()));
        }
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean add(InetSocketAddress address, ByteBuffer bbToCopy, long delayNs) {
        BufferEntry entry = pending.poll();

        if (entry != null) {
            ByteBuffer entryBuffer = entry.getBuffer();

            if (entryBuffer.remaining() < bbToCopy.remaining()) {
                throw new IllegalStateException("Buffer capacity " + entry.getBuffer().remaining()
                    + "  is less than datagram size " + bbToCopy.remaining()
                    + ". Increase buffer size in builder.");
            }

            entryBuffer.put(bbToCopy);
            entryBuffer.flip();

            entry.schedule(address, delayNs);
            entries.addLast(entry);

            return true;
        } else {
            LOGGER.warn("Datagram with {} bytes is dropped because buffer queue has no any free buffers.",
                bbToCopy.remaining());

            return false;
        }
    }

    public void retry(BufferEntry entry) {
        entries.addFirst(entry);
    }

    public BufferEntry request() {
        return entries.pollFirst();
    }

    public void release(BufferEntry entry) {
        entry.getBuffer().clear();
        pending.add(entry);
    }

    public static final class BufferEntry implements Serializable {

        private final ByteBuffer buffer;

        private InetSocketAddress address;

        private long scheduledNs;

        private BufferEntry(int capacity, boolean direct) {
            this.buffer = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
            this.address = null;
            this.scheduledNs = System.nanoTime();
        }

        public void schedule(InetSocketAddress address, long delayNs) {
            this.address = address;
            this.scheduledNs = System.nanoTime() + delayNs;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public long getScheduledNs() {
            return scheduledNs;
        }
    }
}
