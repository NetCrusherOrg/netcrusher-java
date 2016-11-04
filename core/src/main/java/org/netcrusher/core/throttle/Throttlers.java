package org.netcrusher.core.throttle;

import java.util.ArrayList;
import java.util.List;

public final class Throttlers {

    private Throttlers() {
    }

    /**
     * Sum delays
     * @param throttlerFactories Throttler factories
     * @return Combined throttler
     */
    public static ThrottlerFactory sum(final ThrottlerFactory... throttlerFactories) {
        if (throttlerFactories == null || throttlerFactories.length == 0) {
            throw new IllegalArgumentException("Empty throttler array");
        }

        return (clientAddress) -> {
            final List<Throttler> throttlers = new ArrayList<>(throttlerFactories.length);

            for (ThrottlerFactory factory : throttlerFactories) {
                throttlers.add(factory.allocate(clientAddress));
            }

            return (bb) -> {
                long delayNs = 0;
                for (Throttler throttler : throttlers) {
                    delayNs += throttler.calculateDelayNs(bb);
                }
                return delayNs;
            };
        };
    }

    /**
     * Select maximum delay
     * @param throttlerFactories Throttler factories
     * @return Combined throttler
     */
    public static ThrottlerFactory max(final ThrottlerFactory... throttlerFactories) {
        if (throttlerFactories == null || throttlerFactories.length == 0) {
            throw new IllegalArgumentException("Empty throttler array");
        }

        return (clientAddress) -> {
            final List<Throttler> throttlers = new ArrayList<>(throttlerFactories.length);

            for (ThrottlerFactory factory : throttlerFactories) {
                throttlers.add(factory.allocate(clientAddress));
            }

            return (bb) -> {
                long delayNs = Long.MIN_VALUE;
                for (Throttler throttler : throttlers) {
                    delayNs = Math.max(delayNs, throttler.calculateDelayNs(bb));
                }
                return delayNs;
            };
        };
    }

    /**
     * Select minimum delay
     * @param throttlerFactories Throttler factories
     * @return Combined throttler
     */
    public static ThrottlerFactory min(final ThrottlerFactory... throttlerFactories) {
        if (throttlerFactories == null || throttlerFactories.length == 0) {
            throw new IllegalArgumentException("Empty throttler array");
        }

        return (clientAddress) -> {
            final List<Throttler> throttlers = new ArrayList<>(throttlerFactories.length);

            for (ThrottlerFactory factory : throttlerFactories) {
                throttlers.add(factory.allocate(clientAddress));
            }

            return (bb) -> {
                long delayNs = Long.MAX_VALUE;
                for (Throttler throttler : throttlers) {
                    delayNs = Math.min(delayNs, throttler.calculateDelayNs(bb));
                }
                return delayNs;
            };
        };
    }
}
