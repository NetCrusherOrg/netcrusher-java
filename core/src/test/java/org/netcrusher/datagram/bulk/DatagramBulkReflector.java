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

public class DatagramBulkReflector implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramBulkReflector.class);

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

            final SocketAddress socketAddress;
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
                throw new IllegalStateException("Socket address is null");
            }

            bb.flip();
            final boolean emptyDatagram = !bb.hasRemaining();

            processed += bb.remaining();

            final int sent;
            try {
                sent = channel.send(bb, socketAddress);
            } catch (ClosedChannelException | EOFException e) {
                LOGGER.debug("Socket is closed");
                break;
            } catch (PortUnreachableException e) {
                LOGGER.debug("Port is unreachable");
                break;
            } catch (IOException e) {
                LOGGER.error("Exception on read", e);
                break;
            }

            if (sent > 0 || emptyDatagram) {
                if (bb.hasRemaining()) {
                    throw new IllegalStateException("Datagram is splitted");
                }
            } else {
                LOGGER.error("Send failed");
                break;
            }

            datagrams++;
        }

        LOGGER.debug("Reflector loop {} has finished {} datagrams with {} bytes",
            new Object[] { name, datagrams, processed });
    }

}
