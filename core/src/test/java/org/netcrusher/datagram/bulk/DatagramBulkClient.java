package org.netcrusher.datagram.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class DatagramBulkClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkClient.class);

    private static final int RCV_BUFFER_SIZE = 16384;

    private static final int SND_BUFFER_SIZE = 1400;

    private final DatagramChannel incoming;

    private final DatagramChannel outgoing;

    private final long count;

    private final Random random;

    private final Thread rcvThread;

    private final Thread sndThread;

    private final byte[] rcvDigest;

    private final byte[] sndDigest;

    public DatagramBulkClient(InetSocketAddress localAddress, InetSocketAddress remoteAddress, long count)
            throws IOException
    {
        this.incoming = DatagramChannel.open(StandardProtocolFamily.INET);
        this.incoming.configureBlocking(true);
        this.incoming.bind(localAddress);

        this.outgoing = DatagramChannel.open(StandardProtocolFamily.INET);
        this.outgoing.configureBlocking(true);
        this.outgoing.connect(remoteAddress);

        this.count = count;
        this.random = new Random();

        this.sndDigest = new byte[16];
        this.rcvDigest = new byte[16];

        this.rcvThread = new Thread(this::rcvLoop);
        this.rcvThread.setName("Rcv loop");

        this.sndThread = new Thread(this::sndLoop);
        this.sndThread.setName("Snd loop");
    }

    public void open() {
        this.rcvThread.start();
        this.sndThread.start();
    }

    @Override
    public void close() throws IOException {
        incoming.close();
        outgoing.close();

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

    public void rcvLoop() {
        LOGGER.debug("Read loop started");

        ByteBuffer bb = ByteBuffer.allocate(RCV_BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        for (long elapsed = count; elapsed > 0 && !Thread.currentThread().isInterrupted(); ) {
            bb.clear();

            try {
                incoming.receive(bb);
            } catch (ClosedChannelException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (IOException e) {
                LOGGER.error("Exception on read", e);
                break;
            }

            bb.flip();
            elapsed -= bb.limit();

            md.update(bb);
        }

        md.digest(rcvDigest);

        LOGGER.debug("Read loop has finished");
    }

    public void sndLoop() {
        LOGGER.debug("Write loop started");

        ByteBuffer bb = ByteBuffer.allocate(SND_BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        for (long elapsed = count; elapsed > 0 && !Thread.currentThread().isInterrupted(); ) {
            random.nextBytes(bb.array());

            int limit = (int) Math.min(bb.capacity(), elapsed);
            bb.limit(limit);
            bb.position(0);
            md.update(bb);

            bb.position(0);
            try {
                outgoing.write(bb);
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
