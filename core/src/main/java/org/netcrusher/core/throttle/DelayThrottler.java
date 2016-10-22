package org.netcrusher.core.throttle;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * A constant delay for all packets
 */
public class DelayThrottler implements Throttler {

    private final long constantDelayNs;

    private final long jitterDelayNs;

    private final Random random;

    /**
     * Simple delay for all packets
     * @param delay Constant part of delay
     * @param delayTimeUnit Delay time unit
     */
    public DelayThrottler(long delay, TimeUnit delayTimeUnit) {
        this(delay, 0, delayTimeUnit);
    }

    /**
     * Simple delay for all packets
     * @param constantDelay Constant part of delay
     * @param jitterDelay Random part of delay
     * @param delayTimeUnit Delay time unit
     */
    public DelayThrottler(long constantDelay, long jitterDelay, TimeUnit delayTimeUnit) {
        this.constantDelayNs = delayTimeUnit.toNanos(constantDelay);
        this.jitterDelayNs = delayTimeUnit.toNanos(jitterDelay);
        this.random = new Random();
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        long delayNs = constantDelayNs;

        if (jitterDelayNs != 0) {
            delayNs += Math.round(random.nextDouble() * jitterDelayNs);
        }

        return delayNs;
    }
}
