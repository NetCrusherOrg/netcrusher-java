package org.netcrusher.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;

public final class NioUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioUtils.class);

    private NioUtils() {
    }

    public static void closeChannel(AbstractSelectableChannel channel) {
        if (channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.error("Fail to close channel", e);
            }
        }
    }

    public static ByteBuffer copy(ByteBuffer source) {
        ByteBuffer target = ByteBuffer.allocate(source.remaining());

        target.put(source);
        target.flip();

        return target;
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

        int index = text.lastIndexOf(':');
        if (index == -1 || index == text.length() - 1) {
            throw new IllegalArgumentException("Port is found in: " + text);
        }
        if (index == 0) {
            throw new IllegalArgumentException("Host is found in: " + text);
        }

        String host = text.substring(0, index);

        String portStr = text.substring(index + 1, text.length());
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port is not integer in address: " + text);
        }

        InetSocketAddress address;
        try {
            address = new InetSocketAddress(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to parse address: " + text);
        }

        return address;
    }

}
