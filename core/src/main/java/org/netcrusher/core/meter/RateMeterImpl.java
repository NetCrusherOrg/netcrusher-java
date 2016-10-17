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
    public Date created() {
        return new Date(created);
    }

    @Override
    public long countTotal() {
        return totalCount.get();
    }

    @Override
    public long countPeriod(boolean reset) {
        final long count = periodCount.get();

        if (reset) {
            periodCount.addAndGet(-count);
        }

        return count;
    }

    @Override
    public double ratePeriod(boolean reset) {
        final long nowNs = System.nanoTime();
        final long elapsedNs = Math.max(0, nowNs - periodMarkerNs.get());
        final long count = periodCount.get();

        double rate;
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


