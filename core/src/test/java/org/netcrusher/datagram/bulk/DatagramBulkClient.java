package org.netcrusher.datagram.bulk;

import org.netcrusher.datagram.DatagramUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class DatagramBulkClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkClient.class);

    private static final String ERROR_EPERM = "Operation not permitted";

    public static final int RCV_BUFFER_SIZE = 64 * 1024;

    public static final int SND_BUFFER_SIZE = 1400;

    public static final byte[] DATAGRAM_TOKEN = { 0, 1, 2, 3, 4, 5, 6, 7 };

    private final String name;

    private final InetSocketAddress remoteAddress;

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
        this.remoteAddress = remoteAddress;

        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(true);
        this.channel.bind(localAddress);

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
        int datagrams = 0;
        while (processed < count && !Thread.currentThread().isInterrupted()) {
            if (!receive(bb)) {
                break;
            }

            processed += bb.limit();
            datagrams++;

            md.update(DATAGRAM_TOKEN);
            md.update(bb);
        }

        rcvDigest = md.digest();

        LOGGER.debug("Read loop {} has finished {} datagrams with {} bytes",
            new Object[] { name, datagrams, processed });
    }

    private boolean receive(ByteBuffer bb) {
        bb.clear();

        SocketAddress socketAddress;
        try {
            socketAddress = channel.receive(bb);
        } catch (ClosedChannelException | EOFException e) {
            LOGGER.debug("Socket is closed");
            return false;
        } catch (IOException e) {
            LOGGER.error("Exception on read", e);
            return false;
        }

        if (socketAddress == null) {
            throw new IllegalStateException("Socket is null");
        }

        if (!remoteAddress.equals(socketAddress)) {
            throw new IllegalStateException("Packet came from unknown address");
        }

        bb.flip();

        return true;
    }

    public void sndLoop() {
        LOGGER.debug("Write loop {} started", name);

        ByteBuffer bb = ByteBuffer.allocate(SND_BUFFER_SIZE);
        MessageDigest md = createMessageDigest();

        long processed = 0;
        int datagrams = 0;
        while (processed < count && !Thread.currentThread().isInterrupted()) {
            random.nextBytes(bb.array());

            int capacity = (int) Math.min(bb.capacity(), count - processed);
            int limit = (random.nextInt(20) == 0) ? 0 : 1 + random.nextInt(capacity);

            bb.limit(limit);
            bb.position(0);
            md.update(DATAGRAM_TOKEN);
            md.update(bb);

            if (!sent(bb)) {
                break;
            }

            processed += limit;
            datagrams++;
        }

        sndDigest = md.digest();

        LOGGER.debug("Write loop {} has finished {} datagrams with {} bytes",
            new Object[] { name, datagrams, processed });
    }

    private boolean sent(ByteBuffer bb) {
        final boolean emptyDatagram = !bb.hasRemaining();

        while (true) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                LOGGER.debug("Thread is interrupted");
                return false;
            }

            bb.rewind();

            int sent;
            try {
                sent = channel.send(bb, remoteAddress);
            } catch (ClosedChannelException | EOFException e) {
                LOGGER.debug("Socket is closed");
                return false;
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port is unreachable", e);
                return false;
            } catch (SocketException e) {
                try {
                    DatagramUtils.rethrowSocketException(e);
                    continue;
                } catch (SocketException e2) {
                    LOGGER.error("Socket exception on write", e2);
                    return false;
                }
            } catch (IOException e) {
                LOGGER.error("IO exception on write", e);
                return false;
            }

            if (emptyDatagram || sent > 0) {
                if (bb.hasRemaining()) {
                    throw new IllegalStateException("Datagram is splitted");
                }
                return true;
            }

            LOGGER.info("resending");
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
