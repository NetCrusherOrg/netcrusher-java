package org.netcrusher.core.meter;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RateMeterImpl implements RateMeter, Serializable {

    private final long createdMs;

    private final AtomicLong totalCount;

    private final AtomicLong periodCount;

    private final AtomicLong periodMarkerNs;

    public RateMeterImpl() {
        this.createdMs = nowMs();
        this.totalCount = new AtomicLong(0);
        this.periodCount = new AtomicLong(0);
        this.periodMarkerNs = new AtomicLong(nowNs());
    }

    @Override
    public long getTotalElapsedMs() {
        return Math.max(0, nowMs() - createdMs);
    }

    @Override
    public long getTotalCount() {
        return totalCount.get();
    }

    @Override
    public RateMeterPeriod getTotal() {
        return new RateMeterPeriod(getTotalCount(), getTotalElapsedMs());
    }

    @Override
    public RateMeterPeriod getPeriod(boolean reset) {
        final long nowNs = nowNs();
        final long elapsedNs = Math.max(0, nowNs - periodMarkerNs.get());
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs);

        if (reset) {
            periodMarkerNs.set(nowNs);
            final long count = periodCount.getAndSet(0);
            return new RateMeterPeriod(count, elapsedMs);
        } else {
            final long count = periodCount.get();
            return new RateMeterPeriod(count, elapsedMs);
        }
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

    protected long nowNs() {
        return System.nanoTime();
    }

    protected long nowMs() {
        return System.currentTimeMillis();
    }
}


