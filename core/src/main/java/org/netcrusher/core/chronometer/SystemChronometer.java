package org.netcrusher.core.chronometer;

public final class SystemChronometer implements Chronometer {

    public static final Chronometer INSTANCE = new SystemChronometer();

    private SystemChronometer() {
    }

    @Override
    public long getEpochMs() {
        return System.currentTimeMillis();
    }

    @Override
    public long getTickNs() {
        return System.nanoTime();
    }

}
