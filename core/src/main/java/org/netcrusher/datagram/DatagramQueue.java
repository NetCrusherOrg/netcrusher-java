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

    public boolean add(InetSocketAddress address, ByteBuffer bbToCopy, long delayNs) {
        ByteBuffer bb = NioUtils.copy(bbToCopy);
        Entry entry = new Entry(address, bb, delayNs);
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

    public void retry(Entry entry) {
        entries.addFirst(entry);
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

        private final long scheduledNs;

        private Entry(InetSocketAddress address, ByteBuffer buffer, long delayNs) {
            this.address = address;
            this.buffer = buffer;
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
