package org.netcrusher.core.throttle.rate;

import org.netcrusher.core.chronometer.Chronometer;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Throttler limits packets (datagrams) per period
 */
public class PacketRateThrottler extends AbstractRateThrottler {

    /**
     * Create new throttler
     * @param rate How many packets (datagrams) are expected per period
     * @param time Period time
     * @param timeUnit Period time unit
     */
    public PacketRateThrottler(long rate, long time, TimeUnit timeUnit) {
        super(rate, time, timeUnit);
    }

    /**
     * Create new throttler
     * @param rate How many packets (datagrams) are expected per period
     * @param time Period time
     * @param timeUnit Period time unit
     * @param factor Division factor
     */
    public PacketRateThrottler(long rate, long time, TimeUnit timeUnit, int factor) {
        super(rate, time, timeUnit, factor);
    }

    protected PacketRateThrottler(long rate, long time, TimeUnit timeUnit, int factor, Chronometer chronometer) {
        super(rate, time, timeUnit, factor, chronometer);
    }

    @Override
    protected int events(ByteBuffer bb) {
        return +1;
    }

}
