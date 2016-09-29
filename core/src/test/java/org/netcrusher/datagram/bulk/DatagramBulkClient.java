package org.netcrusher.datagram.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class DatagramBulkClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkClient.class);

    public static final int RCV_BUFFER_SIZE = 64 * 1024;

    public static final int SND_BUFFER_SIZE = 1400;

    private final String name;

    private final DatagramChannel channel;

    private final long count;

    private final Random random;

    private final Thread rcvThread;

    private final Thread sndThread;

    private byte[] rcvDigest;

    private byte[] sndDigest;

    public DatagramBulkClient(String name,
                              InetSocketAddress localAddress,
                              InetSocketAddress remoteAddress,
                              long count) throws IOException
    {
        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(true);
        this.channel.bind(localAddress);
        this.channel.connect(remoteAddress);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bulk client {}: BIND<{}> CONNECT<{}>", new Object[]{name, localAddress, remoteAddress});
        }

        this.name = name;
        this.count = count;
        this.random = new Random();

        this.rcvThread = new Thread(this::rcvLoop);
        this.rcvThread.setName("Rcv loop [" + name + "]");

        this.sndThread = new Thread(this::sndLoop);
        this.sndThread.setName("Snd loop [" + name + "]");
    }

    public void open() {
        this.rcvThread.start();
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
            LOGGER.warn("Write thread {} is still alive", name);
        }

        rcvThread.join(timeoutMs);
        if (rcvThread.isAlive()) {
            LOGGER.warn("Read thread {} is still alive", name);
        }
    }

    public byte[] getSndDigest() {
        return sndDigest;
    }

    public byte[] getRcvDigest() {
        return rcvDigest;
    }

    public void rcvLoop() {
        LOGGER.debug("Read loop {} started", name);

        ByteBuffer bb = ByteBuffer.allocate(RCV_BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        long processed = 0;
        while (processed < count && !Thread.currentThread().isInterrupted()) {
            bb.clear();

            SocketAddress socketAddress;
            try {
                socketAddress = channel.receive(bb);
            } catch (ClosedChannelException | EOFException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (IOException e) {
                LOGGER.error("Exception on read", e);
                break;
            }

            if (socketAddress == null) {
                throw new IllegalStateException("Socket is null");
            }

            bb.flip();
            processed += bb.limit();

            md.update(bb);
        }

        rcvDigest = md.digest();

        LOGGER.debug("Read loop {} has finished with {} bytes", name, processed);
    }

    public void sndLoop() {
        LOGGER.debug("Write loop {} started", name);

        ByteBuffer bb = ByteBuffer.allocate(SND_BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        long processed = 0;
        while (processed < count && !Thread.currentThread().isInterrupted()) {
            random.nextBytes(bb.array());

            int limit = (int) Math.min(bb.capacity(), count - processed);
            bb.limit(limit);
            bb.position(0);
            md.update(bb);

            bb.position(0);
            try {
                channel.write(bb);
            } catch (ClosedChannelException | EOFException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port is unreachable", e);
                break;
            } catch (IOException e) {
                LOGGER.error("Exception on write", e);
                break;
            }

            if (bb.hasRemaining()) {
                throw new IllegalStateException("Datagram is splitted");
            }

            // we need to slow down otherwise packets will be lost
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                LOGGER.debug("Thread is interrupted");
                break;
            }

            processed += limit;
        }

        sndDigest = md.digest();

        LOGGER.debug("Write loop {} has finished with {} bytes", name, processed);
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
