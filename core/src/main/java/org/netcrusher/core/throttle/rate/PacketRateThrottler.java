package org.netcrusher.core.throttle.rate;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Throttler limits packets (datagrams) per period
 */
public class PacketRateThrottler extends AbstractRateThrottler {

    /**
     * Create new throttler
     * @param rate How many packets (datagrams) are expected per period
     * @param rateTime Period time
     * @param rateTimeUnit Period time unit
     * @param factor Division factor
     */
    public PacketRateThrottler(long rate, long rateTime, TimeUnit rateTimeUnit, int factor) {
        super(rate, rateTime, rateTimeUnit, factor);
    }

    /**
     * Create new throttler
     * @param rate How many packets (datagrams) are expected per period
     * @param rateTime Period time
     * @param rateTimeUnit Period time unit
     */
    public PacketRateThrottler(long rate, long rateTime, TimeUnit rateTimeUnit) {
        super(rate, rateTime, rateTimeUnit);
    }

    @Override
    protected int events(ByteBuffer bb) {
        return +1;
    }

}
