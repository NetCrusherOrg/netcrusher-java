package org.netcrusher.core.throttle;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * A simple delay for all packets
 */
public class DelayThrottler implements Throttler {

    private final long delayNs;

    public DelayThrottler(long delay, TimeUnit timeUnit) {
        this.delayNs = timeUnit.toNanos(delay);
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        return delayNs;
    }
}
