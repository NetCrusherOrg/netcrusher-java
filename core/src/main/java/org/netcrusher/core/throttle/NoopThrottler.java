package org.netcrusher.core.throttle;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Throttler with no effect
 */
public final class NoopThrottler implements Throttler {

    public static final Throttler INSTANCE = new NoopThrottler();

    private NoopThrottler() {
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        return NO_DELAY;
    }
}
