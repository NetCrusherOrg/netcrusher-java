package org.netcrusher.core.meter;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RateMeterImpl implements RateMeter {

    private static final double ONE_SEC_IN_NS = TimeUnit.SECONDS.toNanos(1);

    private final long created;

    private final AtomicLong totalCount;

    private final AtomicLong periodCount;

    private final AtomicLong periodMarkerNs;

    public RateMeterImpl() {
        this.created = System.currentTimeMillis();
        this.totalCount = new AtomicLong(0);
        this.periodCount = new AtomicLong(0);
        this.periodMarkerNs = new AtomicLong(System.nanoTime());
    }

    @Override
    public long getTotalElapsedMs() {
        return System.currentTimeMillis() - created;
    }

    @Override
    public long getTotalCount() {
        return totalCount.get();
    }

    @Override
    public long getPeriodCount(boolean reset) {
        final long nowNs = System.nanoTime();
        final long count = periodCount.get();

        if (reset) {
            periodMarkerNs.set(nowNs);
            periodCount.addAndGet(-count);
        }

        return count;
    }

    @Override
    public double getPeriodRate(boolean reset) {
        final long nowNs = System.nanoTime();
        final long count = periodCount.get();

        final long elapsedNs = Math.max(0, nowNs - periodMarkerNs.get());

        final double rate;
        if (elapsedNs > 0) {
            rate = ONE_SEC_IN_NS * count / elapsedNs;
        } else {
            rate = 0;
        }

        if (reset) {
            periodMarkerNs.set(nowNs);
            periodCount.addAndGet(-count);
        }

        return rate;
    }

    public void update(long delta) {
        totalCount.addAndGet(delta);
        periodCount.addAndGet(delta);
    }

    public void increment() {
        update(+1);
    }

    public void decrement() {
        update(-1);
    }
}


