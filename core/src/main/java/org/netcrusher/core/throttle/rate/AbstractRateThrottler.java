package org.netcrusher.core.throttle.rate;

import org.netcrusher.core.throttle.Throttler;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRateThrottler implements Throttler {

    private static final long MAX_PERIOD_HOURS = 1;

    private static final long MIN_PERIOD_MILLIS = 10;

    private final long periodNs;

    private final long rate;

    private long markerNs;

    private int count;

    protected AbstractRateThrottler(long rate, long rateTime, TimeUnit rateTimeUnit) {
        if (rate < 1 || rate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Rate value is invalid");
        }

        if (rateTimeUnit.toHours(rateTime) > MAX_PERIOD_HOURS) {
            throw new IllegalArgumentException("Period is too high");
        }
        if (rateTimeUnit.toMillis(rateTime) < MIN_PERIOD_MILLIS) {
            throw new IllegalArgumentException("Period is too small");
        }

        this.periodNs = rateTimeUnit.toNanos(rateTime);
        this.rate = rate;

        this.markerNs = nowNs();
        this.count = 0;
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        final long nowNs = nowNs();
        // elapsed value could be even negative
        final long elapsedNs = nowNs - markerNs;

        count += events(clientAddress, bb);

        long delayNs = 0;

        if (elapsedNs >= periodNs || count >= rate) {
            final double registered = count;
            final double allowed = 1.0 * rate * elapsedNs / periodNs;

            if (registered > allowed) {
                final double excess = registered - allowed;
                delayNs = Math.round(periodNs * excess / rate);
            }

            markerNs = nowNs + delayNs;
            count = 0;
        }

        return delayNs;
    }

    protected long nowNs() {
        return System.nanoTime();
    }

    protected abstract int events(InetSocketAddress clientAddress, ByteBuffer bb);

}
