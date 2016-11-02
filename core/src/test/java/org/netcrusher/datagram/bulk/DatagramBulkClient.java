package org.netcrusher.datagram.bulk;

import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.core.throttle.rate.PacketRateThrottler;
import org.netcrusher.datagram.DatagramUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class DatagramBulkClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkClient.class);

    public static final int MAX_DATAGRAM_SIZE = 1400;

    public static final byte[] DATAGRAM_TOKEN = { 0, 1, 2, 3, 4, 5, 6, 7 };

    private final DatagramChannel channel;

    private final Producer producer;

    private final Consumer consumer;

    public DatagramBulkClient(String name,
                              InetSocketAddress bindAddress,
                              InetSocketAddress connectAddress,
                              long limit,
                              CyclicBarrier readBarrier,
                              CyclicBarrier sentBarrier) throws IOException
    {
        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(true);
        this.channel.bind(bindAddress);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bulk client {}: BIND<{}> CONNECT<{}>",
                new Object[]{name, bindAddress, connectAddress});
        }

        this.consumer = new Consumer(channel, name, limit, connectAddress, readBarrier);
        this.producer = new Producer(channel, name, limit, connectAddress, sentBarrier);
    }

    public void open() throws Exception {
        this.consumer.start();
        this.producer.start();
    }

    @Override
    public void close() throws Exception {
        producer.interrupt();
        consumer.interrupt();

        producer.join();
        consumer.join();

        channel.close();
    }

    public byte[] awaitProducerDigest(long timeoutMs) throws Exception {
        producer.join(timeoutMs);
        return producer.digest;
    }

    public byte[] awaitConsumerDigest(long timeoutMs) throws Exception {
        consumer.join(timeoutMs);
        return consumer.digest;
    }

    private static final class Consumer extends Thread {

        private final DatagramChannel channel;

        private final String name;

        private final long limit;

        private final InetSocketAddress connectAddress;

        private final CyclicBarrier barrier;

        private byte[] digest;

        public Consumer(DatagramChannel channel, String name, long limit,
                        InetSocketAddress connectAddress,
                        CyclicBarrier barrier) {
            this.channel = channel;
            this.name = name;
            this.limit = limit;
            this.connectAddress = connectAddress;
            this.barrier = barrier;
            this.digest = null;
            this.setName("Consumer thread");
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (Exception e) {
                LOGGER.error("Read thread error", e);
            }
        }

        private void loop() throws Exception {
            LOGGER.debug("Read loop {} started", name);

            final int rcvBufferSize = channel.socket().getReceiveBufferSize();
            final ByteBuffer bb = ByteBuffer.allocate(rcvBufferSize);
            final MessageDigest md = createMessageDigest();

            if (barrier != null) {
                barrier.await();
                LOGGER.debug("Read barrier has been passed for {}", name);
            }

            final long markerMs = System.currentTimeMillis();
            long readBytes = 0;
            int readDatagrams = 0;

            try {
                while (readBytes < this.limit && !Thread.currentThread().isInterrupted()) {
                    receive(bb);

                    readBytes += bb.limit();
                    readDatagrams++;

                    md.update(DATAGRAM_TOKEN);
                    md.update(bb);
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Consumer channel is closed for {}", name);
            } catch (Exception e) {
                LOGGER.error("Read loop error", e);
            }

            this.digest = md.digest();

            final long elapsedMs = System.currentTimeMillis() - markerMs;
            LOGGER.debug("Read loop {} has finished {} datagrams with {} bytes in {}ms",
                new Object[] { name, readDatagrams, readBytes, elapsedMs });
        }

        private void receive(ByteBuffer bb) throws IOException {
            bb.clear();

            SocketAddress socketAddress = channel.receive(bb);
            if (socketAddress == null) {
                throw new IllegalStateException("Socket address is null");
            }

            if (!connectAddress.equals(socketAddress)) {
                throw new IllegalStateException("Packet came from unknown address");
            }

            bb.flip();
        }
    }

    private static final class Producer extends Thread {

        private final DatagramChannel channel;

        private final String name;

        private final long limit;

        private final InetSocketAddress connectAddress;

        private final CyclicBarrier barrier;

        private final Throttler throttler;

        private final Random random;

        private byte[] digest;

        public Producer(DatagramChannel channel, String name, long limit,
                        InetSocketAddress connectAddress,
                        CyclicBarrier barrier) {
            this.channel = channel;
            this.throttler = new PacketRateThrottler(30, 50, TimeUnit.MILLISECONDS);
            this.name = name;
            this.limit = limit;
            this.connectAddress = connectAddress;
            this.barrier = barrier;
            this.random = new Random();
            this.digest = null;
            this.setName("Producer thread");
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (Exception e) {
                LOGGER.error("Send thread error", e);
            }
        }

        private void loop() throws Exception {
            LOGGER.debug("Send loop {} started", name);

            final ByteBuffer bb = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
            final MessageDigest md = createMessageDigest();

            if (barrier != null) {
                barrier.await();
                Thread.sleep(1000);
                LOGGER.debug("Sent barrier has been passed for {}", name);
            }

            final long markerMs = System.currentTimeMillis();
            long sentBytes = 0;
            int sentDatagrams = 0;

            try {
                while (sentBytes < limit && !Thread.currentThread().isInterrupted()) {
                    random.nextBytes(bb.array());

                    final int capacity = (int) Math.min(bb.capacity(), limit - sentBytes);
                    final boolean empty = random.nextInt(20) == 0;
                    final int limit = empty ? 0 : 1 + random.nextInt(capacity);

                    bb.limit(limit);
                    bb.position(0);

                    md.update(DATAGRAM_TOKEN);
                    md.update(bb);

                    bb.position(0);

                    long delayNs = throttler.calculateDelayNs(null, bb);
                    if (delayNs > 0) {
                        LOGGER.trace("Sent loop will be suspended on {} ns", delayNs);

                        try {
                            Thread.sleep(delayNs / 1_000_000, (int) (delayNs % 1_000_000));
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                    sent(bb);

                    sentBytes += limit;
                    sentDatagrams++;
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Producer channel is closed for {}", name);
            } catch (Exception e) {
                LOGGER.error("Send loop error", e);
            }

            this.digest = md.digest();

            final long elapsedMs = System.currentTimeMillis() - markerMs;
            LOGGER.debug("Send loop {} has finished {} datagrams with {} bytes in {}ms",
                new Object[] { name, sentDatagrams, sentBytes, elapsedMs });
        }

        private void sent(ByteBuffer bb) throws IOException {
            final boolean emptyDatagram = !bb.hasRemaining();

            while (true) {
                bb.rewind();

                int sent;
                try {
                    sent = channel.send(bb, connectAddress);
                } catch (SocketException e) {
                    DatagramUtils.rethrowSocketException(e);
                    continue;
                }

                if (emptyDatagram || sent > 0) {
                    if (bb.hasRemaining()) {
                        throw new IllegalStateException("Datagram is splitted");
                    } else {
                        break;
                    }
                } else {
                    throw new IllegalStateException("Nothing is sent");
                }
            }
        }
    }

    private static MessageDigest createMessageDigest() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Fail to create digest", e);
        }

        md.reset();

        return md;
    }

}
