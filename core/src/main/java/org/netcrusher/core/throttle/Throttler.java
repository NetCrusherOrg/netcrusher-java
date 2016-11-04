package org.netcrusher.core.throttle;

import org.netcrusher.core.reactor.NioReactor;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Throttling abstraction. As most throttlers have some state an usual throttler instance should
 * not be shared across crushers.
 */
@FunctionalInterface
public interface Throttler {

    long NO_DELAY_NS = -TimeUnit.MILLISECONDS.toNanos(1);

    Throttler NOOP = (bb) -> NO_DELAY_NS;

    /**
     * <p>Calculate delay for the buffer. Return 0 if the buffer should be sent immediately.</p>
     *
     * <p><em>Although returned delay has nanosecond precision the real time granularity
     * is much larger (normal precision is up to tens of millisecond)</em></p>
     *
     * @param bb The buffer with data
     * @return How long the buffer should be postponed before sent (in milliseconds)
     *
     * @see NioReactor#NioReactor(long)
     */
    long calculateDelayNs(ByteBuffer bb);

}
