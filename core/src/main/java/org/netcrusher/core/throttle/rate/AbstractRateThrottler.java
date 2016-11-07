package org.netcrusher.core.throttle.rate;

import org.netcrusher.core.throttle.Throttler;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRateThrottler implements Throttler {

    private static final long MAX_PERIOD_HOURS = 1;

    private static final long MIN_PERIOD_MILLIS = 10;

    private static final long MIN_AUTOFACTOR_PERIOD_MS = 20;

    private static final long MIN_AUTOFACTOR_RATE = 5;

    private final long periodNs;

    private final long rate;

    private long markerNs;

    private int count;

    protected AbstractRateThrottler(long rate, long rateTime, TimeUnit rateTimeUnit) {
        this(rate, rateTime, rateTimeUnit,
            autofactor(rate, rateTime, rateTimeUnit));
    }

    protected AbstractRateThrottler(long rate, long rateTime, TimeUnit rateTimeUnit, int factor) {
        final long effectiveRate = rate / factor;
        final long effectivePeriodNs = rateTimeUnit.toNanos(rateTime) / factor;

        if (effectiveRate < 1 || effectiveRate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Rate value is invalid");
        }

        if (effectivePeriodNs > TimeUnit.HOURS.toNanos(MAX_PERIOD_HOURS)) {
            throw new IllegalArgumentException("Period is too high");
        }
        if (effectivePeriodNs < TimeUnit.MILLISECONDS.toNanos(MIN_PERIOD_MILLIS)) {
            throw new IllegalArgumentException("Period is too small");
        }

        this.periodNs = effectivePeriodNs;
        this.rate = effectiveRate;

        this.markerNs = nowNs();
        this.count = 0;
    }

    @Override
    public long calculateDelayNs(ByteBuffer bb) {
        final long nowNs = nowNs();
        // elapsed value could be even negative
        final long elapsedNs = nowNs - markerNs;

        count += events(bb);

        long delayNs = Throttler.NO_DELAY_NS;

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

    protected abstract int events(ByteBuffer bb);

    private static int autofactor(long rate, long rateTime, TimeUnit rateTimeUnit) {
        final int estimate1 = (int) (rate / MIN_AUTOFACTOR_RATE);
        final int estimate2 = (int) (rateTimeUnit.toMillis(rateTime) / MIN_AUTOFACTOR_PERIOD_MS);

        final int estimate = Math.min(estimate1, estimate2);

        return Math.max(1, estimate);
    }
}
