package org.netcrusher.core.meter;

import org.netcrusher.core.chronometer.Chronometer;
import org.netcrusher.core.chronometer.SystemChronometer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RateMeterImpl implements RateMeter {

    private final long createdMs;

    private final AtomicLong totalCount;

    private final AtomicLong periodCount;

    private final AtomicLong periodMarkerNs;

    private final Chronometer chronometer;

    public RateMeterImpl(Chronometer chronometer) {
        this.chronometer = chronometer;
        this.createdMs = chronometer.getEpochMs();
        this.totalCount = new AtomicLong(0);
        this.periodCount = new AtomicLong(0);
        this.periodMarkerNs = new AtomicLong(chronometer.getTickNs());
    }

    public RateMeterImpl() {
        this(SystemChronometer.INSTANCE);
    }

    @Override
    public long getTotalElapsedMs() {
        return Math.max(0, chronometer.getEpochMs() - createdMs);
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
        final long nowNs = chronometer.getTickNs();
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

}
