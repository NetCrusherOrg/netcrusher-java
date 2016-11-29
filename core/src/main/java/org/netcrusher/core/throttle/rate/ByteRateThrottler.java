package org.netcrusher.core.throttle.rate;

import org.netcrusher.core.chronometer.Chronometer;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Throttler limits byte count per period
 */
public class ByteRateThrottler extends AbstractRateThrottler {

    /**
     * Create a new throttler
     * @param rate How many byte are expected per period
     * @param time Period time
     * @param timeUnit Period time unit
     */
    public ByteRateThrottler(long rate, long time, TimeUnit timeUnit) {
        super(rate, time, timeUnit);
    }

    /**
     * Create a new throttler
     * @param rate How many byte are expected per period
     * @param time Period time
     * @param timeUnit Period time unit
     * @param factor Division factor
     */
    public ByteRateThrottler(long rate, long time, TimeUnit timeUnit, int factor) {
        super(rate, time, timeUnit, factor);
    }

    protected ByteRateThrottler(long rate, long time, TimeUnit timeUnit, int factor, Chronometer chronometer) {
        super(rate, time, timeUnit, factor, chronometer);
    }

    @Override
    protected int events(ByteBuffer bb) {
        return bb.remaining();
    }
}
