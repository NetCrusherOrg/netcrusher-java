package org.netcrusher.core.chronometer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MockChronometer implements Chronometer {

    private final AtomicLong epochMs;

    private final AtomicLong tickNs;

    public MockChronometer() {
        this.epochMs = new AtomicLong(System.currentTimeMillis());
        this.tickNs = new AtomicLong(System.nanoTime());
    }

    public void setEpochMs(long value) {
        this.epochMs.set(value);
    }

    @Override
    public long getEpochMs() {
        return epochMs.get();
    }

    public void setTickNs(long value) {
        this.tickNs.set(value);
    }

    @Override
    public long getTickNs() {
        return tickNs.get();
    }

    public void add(long time, TimeUnit timeUnit) {
        this.epochMs.addAndGet(timeUnit.toMillis(time));
        this.tickNs.addAndGet(timeUnit.toNanos(time));
    }

}
