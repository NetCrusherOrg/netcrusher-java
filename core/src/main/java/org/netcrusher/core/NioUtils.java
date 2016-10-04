package org.netcrusher.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

}
