package org.netcrusher.core.reactor;

import java.util.concurrent.TimeUnit;

public class NioSelectorScheduledOp implements Runnable, Comparable<NioSelectorScheduledOp> {

    private final long scheduledNs;

    private final Runnable delegate;

    public NioSelectorScheduledOp(long scheduledNs, Runnable delegate) {
        this.scheduledNs = scheduledNs;
        this.delegate = delegate;
    }

    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public int compareTo(NioSelectorScheduledOp that) {
        long nowNs = System.nanoTime();
        return Long.compare(this.scheduledNs - nowNs, that.scheduledNs - nowNs);
    }

    boolean isReady(long tickMs) {
        long nowNs = System.nanoTime();
        return (scheduledNs - nowNs) < TimeUnit.MILLISECONDS.toNanos(tickMs / 2);
    }

}
