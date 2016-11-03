package org.netcrusher.datagram.bulk;

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
import java.util.concurrent.CyclicBarrier;

public class DatagramBulkReflector implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkReflector.class);

    private final DatagramChannel channel;

    private final Reflector reflector;

    public DatagramBulkReflector(String name, InetSocketAddress bindAddress,
                                 long limit,
                                 CyclicBarrier readBarrier) throws IOException {
        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(true);
        this.channel.bind(bindAddress);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bulk reflector {}: BIND<{}>", new Object[]{name, bindAddress});
        }

        this.reflector = new Reflector(channel, name, limit, readBarrier);
    }

    public void open() {
        this.reflector.start();
    }

    @Override
    public void close() throws Exception {
        this.reflector.interrupt();
        this.reflector.join();

        this.channel.close();
    }

    public DatagramBulkResult awaitReflectorResult(long timeoutMs) throws Exception {
        reflector.join(timeoutMs);

        if (!reflector.isAlive() && reflector.result != null) {
            return reflector.result;
        } else {
            close();
            throw new IllegalStateException("Reflector is still alive");
        }
    }

    private static class Reflector extends Thread {

        private final DatagramChannel channel;

        private final String name;

        private final CyclicBarrier barrier;

        private final long limit;

        private DatagramBulkResult result;

        public Reflector(DatagramChannel channel, String name, long limit, CyclicBarrier barrier) {
            this.channel = channel;
            this.name = name;
            this.barrier = barrier;
            this.limit = limit;
            this.result = null;

            this.setName("Reflector thread");
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (Exception e) {
                LOGGER.error("Reflector thread exception", e);
            }
        }

        public void loop() throws Exception {
            LOGGER.debug("Reflector loop {} started", name);

            final int rcvBufferSize = channel.socket().getReceiveBufferSize();
            final ByteBuffer bb = ByteBuffer.allocate(rcvBufferSize);
            final MessageDigest md5 = Md5DigestFactory.createDigest();

            if (barrier != null) {
                barrier.await();
                LOGGER.debug("Read barrier has been passed for {}", name);
            }

            final long markerMs = System.currentTimeMillis();
            long processedBytes = 0;
            int processedDatagrams = 0;

            try {
                while (processedDatagrams < this.limit && !Thread.currentThread().isInterrupted()) {
                    bb.clear();

                    final SocketAddress socketAddress = channel.receive(bb);
                    if (socketAddress == null) {
                        throw new IllegalStateException("Socket address is null");
                    }

                    bb.flip();
                    final int read = bb.remaining();

                    send(bb, socketAddress);

                    bb.rewind();
                    md5.update(Md5DigestFactory.HEAD_TOKEN);
                    md5.update(bb);

                    processedBytes += read;
                    processedDatagrams++;
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Reflector channel is closed for {}", name);
            } catch (Exception e) {
                LOGGER.error("Reflector loop exception for {}", name, e);
            }

            final long elapsedMs = System.currentTimeMillis() - markerMs;

            DatagramBulkResult result = new DatagramBulkResult();
            result.setElapsedMs(elapsedMs);
            result.setCount(processedDatagrams);
            result.setBytes(processedBytes);
            result.setDigest(md5.digest());

            LOGGER.debug("Reflector loop {} has finished: {}", name, result);

            this.result = result;
        }

        private void send(ByteBuffer bb, SocketAddress socketAddress) throws IOException {
            final boolean emptyDatagram = !bb.hasRemaining();

            while (true) {
                bb.rewind();

                final int sent;
                try {
                    sent = channel.send(bb, socketAddress);
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
                    throw new IllegalStateException("Send failed");
                }
            }
        }

    }

}
