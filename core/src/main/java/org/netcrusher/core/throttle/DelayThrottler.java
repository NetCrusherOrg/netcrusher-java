package org.netcrusher.core.throttle;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * A constant delay for all packets
 */
public class DelayThrottler implements Throttler {

    private final long delayNs;

    /**
     * Constant delay for all packets
     * @param delay Delay
     * @param delayUnit Delay time unit
     */
    public DelayThrottler(long delay, TimeUnit delayUnit) {
        this.delayNs = delayUnit.toNanos(delay);
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        return delayNs;
    }
}
