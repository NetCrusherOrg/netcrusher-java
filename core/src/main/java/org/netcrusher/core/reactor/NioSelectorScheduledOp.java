package org.netcrusher.core.reactor;

public class NioSelectorScheduledOp implements Runnable {

    private final long scheduledNs;

    private final Runnable delegate;

    NioSelectorScheduledOp(long scheduledNs, Runnable delegate) {
        this.scheduledNs = scheduledNs;
        this.delegate = delegate;
    }

    @Override
    public void run() {
        delegate.run();
    }

    boolean isReady() {
        return scheduledNs <= System.nanoTime();
    }

    long getScheduledNs() {
        return scheduledNs;
    }
}
