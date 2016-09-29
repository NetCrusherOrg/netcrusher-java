package org.netcrusher.tcp.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class TcpBulkClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpBulkClient.class);

    private static final int BUFFER_SIZE = 64 * 1024;

    private final String name;

    private final SocketChannel channel;

    private final long count;

    private final Random random;

    private final Thread rcvThread;

    private final Thread sndThread;

    private byte[] rcvDigest;

    private byte[] sndDigest;

    protected TcpBulkClient(String name, SocketChannel channel, long count) {
        this.name = name;
        this.channel = channel;
        this.count = count;
        this.random = new Random();

        this.rcvThread = new Thread(this::rcvLoop);
        this.rcvThread.setName("Rcv loop [" + name + "]");
        this.rcvThread.start();

        this.sndThread = new Thread(this::sndLoop);
        this.sndThread.setName("Snd loop [" + name + "]");
        this.sndThread.start();
    }

    @Override
    public void close() throws IOException {
        channel.close();

        boolean interrupted = false;

        sndThread.interrupt();
        try {
            sndThread.join();
        } catch (InterruptedException e) {
            interrupted = true;
        }

        rcvThread.interrupt();
        try {
            rcvThread.join();
        } catch (InterruptedException e) {
            interrupted = true;
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void await(long timeoutMs) throws InterruptedException {
        sndThread.join(timeoutMs);
        if (sndThread.isAlive()) {
            LOGGER.warn("Write thread is still alive");
        }

        rcvThread.join(timeoutMs);
        if (rcvThread.isAlive()) {
            LOGGER.warn("Read thread is still alive");
        }
    }

    public byte[] getSndDigest() {
        return sndDigest;
    }

    public byte[] getRcvDigest() {
        return rcvDigest;
    }

    public static TcpBulkClient forSocket(String name, SocketChannel channel, long count) {
        return new TcpBulkClient(name, channel, count);
    }

    public static TcpBulkClient forAddress(String name, InetSocketAddress address, long count) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socketChannel.connect(address);

        return new TcpBulkClient(name, socketChannel, count);
    }

    public void rcvLoop() {
        LOGGER.debug("Read loop {} started", name);

        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        for (long elapsed = count; elapsed > 0 && !Thread.currentThread().isInterrupted(); ) {
            bb.clear();

            int read;
            try {
                read = channel.read(bb);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (IOException e) {
                LOGGER.error("Exception on read", e);
                break;
            }

            if (read < 0) {
                LOGGER.debug("End of stream");
                break;
            }

            bb.flip();
            md.update(bb);

            elapsed -= read;
        }

        rcvDigest = md.digest();

        LOGGER.debug("Read loop {} has finished", name);
    }

    public void sndLoop() {
        LOGGER.debug("Write loop {} started", name);

        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        for (long elapsed = count; elapsed > 0 && !Thread.currentThread().isInterrupted(); ) {
            random.nextBytes(bb.array());

            int limit = (int) Math.min(bb.capacity(), elapsed);
            bb.limit(limit);

            bb.position(0);
            md.update(bb);

            bb.position(0);
            try {
                while (bb.hasRemaining()) {
                    channel.write(bb);
                }
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (IOException e) {
                LOGGER.error("Exception on write", e);
                break;
            }

            elapsed -= limit;
        }

        sndDigest = md.digest();

        LOGGER.debug("Write loop {} has finished", name);
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
