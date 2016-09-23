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

    private final SocketChannel channel;

    private final long count;

    private final Random random;

    private final Thread rcvThread;

    private final Thread sndThread;

    private final byte[] rcvDigest;

    private final byte[] sndDigest;

    protected TcpBulkClient(SocketChannel channel, long count) {
        this.channel = channel;
        this.count = count;
        this.random = new Random();

        this.sndDigest = new byte[16];
        this.rcvDigest = new byte[16];

        this.rcvThread = new Thread(this::rcvLoop);
        this.rcvThread.setName("Rcv loop");
        this.rcvThread.start();

        this.sndThread = new Thread(this::sndLoop);
        this.sndThread.setName("Snd loop");
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
        rcvThread.join(timeoutMs);
    }

    public byte[] getSndDigest() {
        return sndDigest;
    }

    public byte[] getRcvDigest() {
        return rcvDigest;
    }

    public static TcpBulkClient forSocket(SocketChannel channel, long count) {
        return new TcpBulkClient(channel, count);
    }

    public static TcpBulkClient forAddress(InetSocketAddress address, long count) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socketChannel.connect(address);

        return new TcpBulkClient(socketChannel, count);
    }

    public void rcvLoop() {
        LOGGER.debug("Read loop started");

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

        md.digest(rcvDigest);

        LOGGER.debug("Read loop has finished");
    }

    public void sndLoop() {
        LOGGER.debug("Write loop started");

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

        md.digest(sndDigest);

        LOGGER.debug("Write loop has finished");
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
