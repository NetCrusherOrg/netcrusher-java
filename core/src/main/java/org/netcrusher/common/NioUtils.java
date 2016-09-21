package org.netcrusher.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;

public final class NioUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioUtils.class);

    private NioUtils() {
    }

    public static void closeChannel(Channel channel) {
        if (channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.error("Fail to close socket channel", e);
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

}
