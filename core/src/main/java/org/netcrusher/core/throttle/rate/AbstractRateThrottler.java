package org.netcrusher.core.throttle.rate;

import org.netcrusher.core.chronometer.Chronometer;
import org.netcrusher.core.chronometer.SystemChronometer;
import org.netcrusher.core.throttle.Throttler;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRateThrottler implements Throttler {

    public static final int AUTO_FACTOR = 0;

    public static final long MAX_PERIOD_HOURS = 1;

    public static final long MIN_PERIOD_MILLIS = 10;

    public static final long MIN_AUTOFACTOR_PERIOD_MS = 20;

    public static final long MIN_AUTOFACTOR_RATE = 5;

    private final long periodNs;

    private final long rate;

    private final Chronometer chronometer;

    private long markerNs;

    private int count;

    protected AbstractRateThrottler(long rate, long time, TimeUnit timeUnit) {
        this(rate, time, timeUnit, AUTO_FACTOR);
    }

    protected AbstractRateThrottler(long rate, long time, TimeUnit timeUnit, int factor) {
        this(rate, time, timeUnit, factor, SystemChronometer.INSTANCE);
    }

    protected AbstractRateThrottler(long rate, long time, TimeUnit timeUnit, int factor, Chronometer chronometer) {
        final long effectiveRate;
        final long effectivePeriodNs;

        if (factor > 0) {
            effectiveRate = rate / factor;
            effectivePeriodNs = timeUnit.toNanos(time) / factor;
        } else {
            final int autofactor = autofactor(rate, time, timeUnit);

            effectiveRate = rate / autofactor;
            effectivePeriodNs = timeUnit.toNanos(time) / autofactor;
        }

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
        this.chronometer = chronometer;

        this.markerNs = chronometer.getTickNs();
        this.count = 0;
    }

    @Override
    public long calculateDelayNs(ByteBuffer bb) {
        final long nowNs = chronometer.getTickNs();
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

    protected abstract int events(ByteBuffer bb);

    private static int autofactor(long rate, long time, TimeUnit timeUnit) {
        final int estimate1 = (int) (rate / MIN_AUTOFACTOR_RATE);
        final int estimate2 = (int) (timeUnit.toMillis(time) / MIN_AUTOFACTOR_PERIOD_MS);

        final int estimate = Math.min(estimate1, estimate2);

        return Math.max(1, estimate);
    }
}
