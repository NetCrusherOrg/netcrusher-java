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

public class DatagramBulkReflector implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkReflector.class);

    private static final String ERROR_EPERM = "Operation not permitted";

    private static final int RCV_BUFFER_SIZE = 64 * 1024;

    private final String name;

    private final DatagramChannel channel;

    private final Thread thread;

    public DatagramBulkReflector(String name, InetSocketAddress localAddress) throws IOException {
        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(true);
        this.channel.bind(localAddress);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bulk reflector {}: BIND<{}>", new Object[]{name, localAddress});
        }

        this.name = name;

        this.thread = new Thread(this::loop);
        this.thread.setName("Reflector loop [" + name + "]");
    }

    public void open() {
        this.thread.start();
    }

    @Override
    public void close() throws IOException {
        channel.close();

        boolean interrupted = false;

        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            interrupted = true;
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void loop() {
        LOGGER.debug("Reflector loop {} started", name);

        ByteBuffer bb = ByteBuffer.allocate(RCV_BUFFER_SIZE);

        long processed = 0;
        int datagrams = 0;
        while (!Thread.currentThread().isInterrupted()) {
            bb.clear();

            final SocketAddress socketAddress = read(bb);
            if (socketAddress == null) {
                break;
            }

            bb.flip();
            processed += bb.remaining();

            final boolean successSend = send(bb, socketAddress);
            if (!successSend) {
                break;
            }

            datagrams++;
        }

        LOGGER.debug("Reflector loop {} has finished {} datagrams with {} bytes",
            new Object[] { name, datagrams, processed });
    }

    private SocketAddress read(ByteBuffer bb) {
        final SocketAddress socketAddress;
        try {
            socketAddress = channel.receive(bb);
        } catch (ClosedChannelException | EOFException e) {
            LOGGER.debug("Socket is closed");
            return null;
        } catch (IOException e) {
            LOGGER.error("Exception on read", e);
            return null;
        }

        if (socketAddress == null) {
            throw new IllegalStateException("Socket address is null");
        }

        return socketAddress;
    }

    private boolean send(ByteBuffer bb, SocketAddress socketAddress) {
        final boolean emptyDatagram = !bb.hasRemaining();

        while (true) {
            bb.rewind();

            final int sent;
            try {
                sent = channel.send(bb, socketAddress);
            } catch (ClosedChannelException | EOFException e) {
                LOGGER.debug("Socket is closed");
                return false;
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port is unreachable");
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

            if (sent > 0 || emptyDatagram) {
                if (bb.hasRemaining()) {
                    throw new IllegalStateException("Datagram is splitted");
                }
            } else {
                LOGGER.error("Send failed");
                return false;
            }

            break;
        }

        return true;
    }

}
