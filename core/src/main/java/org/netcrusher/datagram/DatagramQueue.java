package org.netcrusher.datagram;

import org.netcrusher.core.NioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

public class DatagramQueue implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramQueue.class);

    private static final int LIMIT_COUNT = 16 * 1024;

    private final Deque<Entry> entries;

    public DatagramQueue() {
        this.entries = new LinkedList<>();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean add(InetSocketAddress address, ByteBuffer bbToCopy) {
        ByteBuffer bb = NioUtils.copy(bbToCopy);
        Entry entry = new Entry(address, bb);
        return add(entry);
    }

    public boolean add(Entry entry) {
        if (entries.size() > LIMIT_COUNT) {
            LOGGER.warn("Pending limit is exceeded ({} datagrams). Packet is dropped", entries.size());
            return false;
        }

        entries.addLast(entry);

        return true;
    }

    public boolean retry(Entry entry) {
        if (entry.buffer.hasRemaining()) {
            entries.addFirst(entry);
            return true;
        } else {
            release(entry);
            return false;
        }
    }

    public Entry request() {
        return entries.pollFirst();
    }

    public void release(Entry entry) {
        // nothing to do yet
    }

    public static final class Entry implements Serializable {

        private final InetSocketAddress address;

        private final ByteBuffer buffer;

        public Entry(InetSocketAddress address, ByteBuffer buffer) {
            this.address = address;
            this.buffer = buffer;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }
}
