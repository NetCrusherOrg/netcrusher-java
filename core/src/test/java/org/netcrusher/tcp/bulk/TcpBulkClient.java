package org.netcrusher.tcp.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class TcpBulkClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpBulkClient.class);

    private static final int BUFFER_SIZE = 64 * 1024;

    private final SocketChannel channel;

    private final Producer producer;

    private final Consumer consumer;

    public static TcpBulkClient forSocket(String name, SocketChannel channel, long limit) {
        TcpBulkClient client = new TcpBulkClient(name, channel, limit);
        client.open();

        return client;
    }

    public static TcpBulkClient forAddress(String name, InetSocketAddress address, long limit) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(address);

        return forSocket(name, channel, limit);
    }

    public TcpBulkClient(String name, SocketChannel channel, long limit) {
        this.channel = channel;

        this.producer = new Producer(channel, name, limit);
        this.consumer = new Consumer(channel, name, limit);
    }

    public void open() {
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

    public TcpBulkResult awaitProducerResult(long timeoutMs) throws Exception {
        producer.join(timeoutMs);

        if (!producer.isAlive() && producer.result != null) {
            return producer.result;
        } else {
            close();
            throw new IllegalStateException("Producer is still alive");
        }
    }

    public TcpBulkResult awaitConsumerResult(long timeoutMs) throws Exception {
        consumer.join(timeoutMs);

        if (!consumer.isAlive() && consumer.result != null) {
            return consumer.result;
        } else {
            close();
            throw new IllegalStateException("Consumer is still alive");
        }
    }

    private static class Consumer extends Thread {

        private final SocketChannel channel;

        private final String name;

        private final long limit;

        private TcpBulkResult result;

        public Consumer(SocketChannel channel, String name, long limit) {
            this.channel = channel;
            this.name = name;
            this.limit = limit;
            this.result = null;
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (Exception e) {
                LOGGER.error("Consumer thread error", e);
            }
        }

        public void loop() throws Exception {
            LOGGER.debug("Read loop {} started", name);

            final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
            final MessageDigest md = createMessageDigest();

            final long markerMs = System.currentTimeMillis();
            long readBytes = 0;

            try {
                while (readBytes < limit && !Thread.currentThread().isInterrupted()) {
                    bb.clear();

                    int read = channel.read(bb);

                    if (read < 0) {
                        LOGGER.debug("End of stream");
                        break;
                    }

                    bb.flip();
                    md.update(bb);

                    readBytes += read;
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
            } catch (IOException e) {
                LOGGER.error("Exception on read", e);
            }

            final long elapsedMs = System.currentTimeMillis() - markerMs;

            this.result = new TcpBulkResult();
            this.result.setDigest(md.digest());
            this.result.setElapsedMs(elapsedMs);
            this.result.setBytes(readBytes);

            LOGGER.debug("Read loop {} has finished with {} bytes in {}ms",
                new Object[] { name, readBytes, elapsedMs });
        }
    }

    private static class Producer extends Thread {

        private final SocketChannel channel;

        private final String name;

        private final long limit;

        private final Random random;

        private TcpBulkResult result;

        public Producer(SocketChannel channel, String name, long limit) {
            this.channel = channel;
            this.name = name;
            this.limit = limit;
            this.random = new Random();
            this.result = null;
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (Exception e) {
                LOGGER.error("Producer thread error", e);
            }
        }

        private void loop() throws Exception {
            LOGGER.debug("Send loop {} started", name);

            final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
            final MessageDigest md = createMessageDigest();

            final long markerMs = System.currentTimeMillis();
            long sentBytes = 0;

            try {
                while (sentBytes < limit && !Thread.currentThread().isInterrupted()) {
                    random.nextBytes(bb.array());

                    final int capacity = (int) Math.min(bb.capacity(), limit - sentBytes);

                    bb.limit(capacity);
                    bb.position(0);
                    md.update(bb);

                    bb.position(0);

                    while (bb.hasRemaining()) {
                        int sent = channel.write(bb);
                        sentBytes += sent;
                    }
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
            } catch (IOException e) {
                LOGGER.error("Exception on write", e);
            }

            final long elapsedMs = System.currentTimeMillis() - markerMs;

            this.result = new TcpBulkResult();
            this.result.setDigest(md.digest());
            this.result.setElapsedMs(elapsedMs);
            this.result.setBytes(sentBytes);

            LOGGER.debug("Send loop {} has finished with {} bytes in {}ms",
                new Object[] { name, sentBytes, elapsedMs });
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
