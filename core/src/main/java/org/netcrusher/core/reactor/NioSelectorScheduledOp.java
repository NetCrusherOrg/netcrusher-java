package org.netcrusher.core.reactor;

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
        return Long.compare(this.scheduledNs, that.scheduledNs);
    }

    boolean isReady() {
        return scheduledNs <= System.nanoTime();
    }

}
