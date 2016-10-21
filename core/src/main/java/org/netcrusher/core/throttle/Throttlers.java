package org.netcrusher.core.throttle;

public final class Throttlers {

    private Throttlers() {
    }

    /**
     * Sum delays
     * @param throttlers Throttler instances
     * @return Combined throttler
     */
    public static Throttler sum(final Throttler... throttlers) {
        if (throttlers == null || throttlers.length == 0) {
            throw new IllegalArgumentException("Empty throttler array");
        }

        return (clientAddress, bb) -> {
            long delayNs = 0;
            for (Throttler throttler : throttlers) {
                delayNs += throttler.calculateDelayNs(clientAddress, bb);
            }
            return delayNs;
        };
    }

    /**
     * Select maximum delay
     * @param throttlers Throttler instances
     * @return Combined throttler
     */
    public static Throttler max(final Throttler... throttlers) {
        if (throttlers == null || throttlers.length == 0) {
            throw new IllegalArgumentException("Empty throttler array");
        }

        return (clientAddress, bb) -> {
            long delayNs = 0;
            for (Throttler throttler : throttlers) {
                delayNs = Math.max(delayNs, throttler.calculateDelayNs(clientAddress, bb));
            }
            return delayNs;
        };
    }

    /**
     * Select minimum delay
     * @param throttlers Throttler instances
     * @return Combined throttler
     */
    public static Throttler min(final Throttler... throttlers) {
        if (throttlers == null || throttlers.length == 0) {
            throw new IllegalArgumentException("Empty throttler array");
        }

        return (clientAddress, bb) -> {
            long delayNs = Long.MAX_VALUE;
            for (Throttler throttler : throttlers) {
                delayNs = Math.min(delayNs, throttler.calculateDelayNs(clientAddress, bb));
            }
            return delayNs;
        };
    }
}
