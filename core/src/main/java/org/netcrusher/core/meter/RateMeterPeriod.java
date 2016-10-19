package org.netcrusher.core.meter;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class RateMeterPeriod implements Serializable {

    private static final long MS_PER_SEC = TimeUnit.SECONDS.toMillis(1);

    private final long count;

    private final long elapsedMs;

    RateMeterPeriod(long count, long elapsedMs) {
        this.count = count;
        this.elapsedMs = elapsedMs;
    }

    /**
     * Get count of events/bytes/
     * @return Counter
     */
    public long getCount() {
        return count;
    }

    /**
     * Get period duration in milliseconds
     * @return Duration
     */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * Get rate per specified amount of time
     * @param time Amount of time
     * @param timeUnit Time unit
     * @return Rate for the period
     */
    public double getRatePer(long time, TimeUnit timeUnit) {
        if (elapsedMs > 0) {
            return 1.0 * timeUnit.toMillis(time) * count / elapsedMs;
        } else {
            return Double.NaN;
        }
    }

    /**
     * Get rate per second
     * @return Rate for the period
     */
    public double getRatePerSec() {
        if (elapsedMs > 0) {
            return 1.0 * MS_PER_SEC * count / elapsedMs;
        } else {
            return Double.NaN;
        }
    }

}
