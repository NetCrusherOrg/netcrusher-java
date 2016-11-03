package org.netcrusher.core.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;

public final class NioUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioUtils.class);

    private NioUtils() {
    }

    public static ByteBuffer allocaleByteBuffer(int capacity, boolean direct) {
        return direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
    }

    public static void close(AbstractSelectableChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.error("Fail to close channel", e);
            }
        }
    }

    public static void closeNoLinger(SocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.setOption(StandardSocketOptions.SO_LINGER, 0);
            } catch (IOException e) {
                LOGGER.error("Fail to set SO_LINGER on channel", e);
            }

            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.error("Fail to close channel", e);
            }
        }
    }

    public static void close(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.error("Unexpected exception on close", e);
            }
        }
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOGGER.error("Unexpected exception on close", e);
            }
        }
    }

    public static void setupInterestOps(SelectionKey selectionKey, int options) {
        if ((selectionKey.interestOps() & options) != options) {
            selectionKey.interestOps(selectionKey.interestOps() | options);
        }
    }

    public static void clearInterestOps(SelectionKey selectionKey, int options) {
        if ((selectionKey.interestOps() & options) != 0) {
            selectionKey.interestOps(selectionKey.interestOps() & ~options);
        }
    }

    public static InetSocketAddress parseInetSocketAddress(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Address is empty");
        }

        final int index = text.lastIndexOf(':');
        if (index == -1 || index == text.length() - 1) {
            throw new IllegalArgumentException("Port is not found in: " + text);
        }
        if (index == 0) {
            throw new IllegalArgumentException("Host is not found in: " + text);
        }

        final String host = text.substring(0, index);

        final String portStr = text.substring(index + 1, text.length());
        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port is not integer in address: " + text);
        }

        final InetSocketAddress address;
        try {
            address = new InetSocketAddress(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to parse address: " + text);
        }

        return address;
    }

}
