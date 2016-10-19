package org.netcrusher.core.throttle;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Throttler with no effect
 * @see NoopThrottler#INSTANCE
 */
public final class NoopThrottler implements Throttler {

    /**
     * The single instance of this no-state throttler
     */
    public static final Throttler INSTANCE = new NoopThrottler();

    private NoopThrottler() {
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        return NO_DELAY;
    }

    @Override
    public Throttler combine(Throttler other) {
        return other;
    }
}
