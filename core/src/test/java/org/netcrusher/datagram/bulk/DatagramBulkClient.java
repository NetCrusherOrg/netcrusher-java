package org.netcrusher.datagram.bulk;

import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.core.throttle.rate.PacketRateThrottler;
import org.netcrusher.datagram.DatagramUtils;
import org.netcrusher.test.Md5DigestFactory;
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
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class DatagramBulkClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkClient.class);

    private static final int MAX_DATAGRAM_SIZE = 1400;

    private static final long SENDER_PAUSE_MS = 1000;

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
        if (producer.isAlive()) {
            producer.interrupt();
            producer.join();
        }

        if (consumer.isAlive()) {
            consumer.interrupt();
            consumer.join();
        }

        if (channel.isOpen()) {
            channel.close();
        }
    }

    public DatagramBulkResult awaitProducerResult(long timeoutMs) throws Exception {
        producer.join(timeoutMs);

        if (!producer.isAlive() && producer.result != null) {
            return producer.result;
        } else {
            close();
            throw new IllegalStateException("Producer is still alive");
        }
    }

    public DatagramBulkResult awaitConsumerResult(long timeoutMs) throws Exception {
        consumer.join(timeoutMs);

        if (!consumer.isAlive() && consumer.result != null) {
            return consumer.result;
        } else {
            close();
            throw new IllegalStateException("Consumer is still alive");
        }
    }

    private static final class Consumer extends Thread {

        private final DatagramChannel channel;

        private final String name;

        private final long limit;

        private final InetSocketAddress connectAddress;

        private final CyclicBarrier barrier;

        private DatagramBulkResult result;

        public Consumer(DatagramChannel channel, String name, long limit,
                        InetSocketAddress connectAddress,
                        CyclicBarrier barrier) {
            this.channel = channel;
            this.name = name;
            this.limit = limit;
            this.connectAddress = connectAddress;
            this.barrier = barrier;
            this.result = null;

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
            final MessageDigest md5 = Md5DigestFactory.createDigest();

            if (barrier != null) {
                barrier.await();
                LOGGER.debug("Read barrier has been passed for {}", name);
            }

            final long markerMs = System.currentTimeMillis();
            long readBytes = 0;
            int readDatagrams = 0;

            try {
                while (readDatagrams < this.limit && !Thread.currentThread().isInterrupted()) {
                    receive(bb);

                    readBytes += bb.limit();
                    readDatagrams++;

                    md5.update(Md5DigestFactory.HEAD_TOKEN);
                    md5.update(bb);
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Consumer channel is closed for {}", name);
            } catch (Exception e) {
                LOGGER.error("Read loop error", e);
            }

            final long elapsedMs = System.currentTimeMillis() - markerMs - SENDER_PAUSE_MS;

            final DatagramBulkResult result = new DatagramBulkResult();
            result.setDigest(md5.digest());
            result.setElapsedMs(elapsedMs);
            result.setCount(readDatagrams);
            result.setBytes(readBytes);

            LOGGER.debug("Read loop {} has finished {}", name, result);

            this.result = result;
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

        private static final int DATAGRAM_PER_SEC = 100;

        private final DatagramChannel channel;

        private final String name;

        private final long limit;

        private final InetSocketAddress connectAddress;

        private final CyclicBarrier barrier;

        private final Throttler throttler;

        private final Random random;

        private DatagramBulkResult result;

        public Producer(DatagramChannel channel, String name, long limit,
                        InetSocketAddress connectAddress,
                        CyclicBarrier barrier) {
            this.channel = channel;
            this.throttler = new PacketRateThrottler(DATAGRAM_PER_SEC, 1, TimeUnit.SECONDS);
            this.name = name;
            this.limit = limit;
            this.connectAddress = connectAddress;
            this.barrier = barrier;
            this.random = new Random();
            this.result = null;

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
            final MessageDigest md5 = Md5DigestFactory.createDigest();

            if (barrier != null) {
                barrier.await();
                Thread.sleep(SENDER_PAUSE_MS);
                LOGGER.debug("Sent barrier has been passed for {}", name);
            }

            final long markerMs = System.currentTimeMillis();
            long sentBytes = 0;
            int sentDatagrams = 0;

            try {
                while (sentDatagrams < this.limit && !Thread.currentThread().isInterrupted()) {
                    random.nextBytes(bb.array());

                    final boolean empty = random.nextInt(20) == 0;
                    final int limit = empty ? 0 : 1 + random.nextInt(bb.capacity());

                    bb.limit(limit);
                    bb.rewind();

                    md5.update(Md5DigestFactory.HEAD_TOKEN);
                    md5.update(bb);

                    bb.rewind();

                    long delayNs = throttler.calculateDelayNs(bb);
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

            final long elapsedMs = System.currentTimeMillis() - markerMs;

            DatagramBulkResult result = new DatagramBulkResult();
            result.setDigest(md5.digest());
            result.setElapsedMs(elapsedMs);
            result.setCount(sentDatagrams);
            result.setBytes(sentBytes);

            LOGGER.debug("Send loop {} has finished: {}", name, result);

            this.result = result;
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

}
