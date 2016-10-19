package org.netcrusher.core.throttle;

import org.netcrusher.core.NioReactor;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Throttling abstraction. As most throttlers have some state an usual throttler instance should
 * not be shared across crushers.
 */
@FunctionalInterface
public interface Throttler {

    /**
     * <p>Calculate delay for the buffer. Return 0 if the buffer should be sent immediately.</p>
     *
     * <p><em>Although returned delay has nanosecond precision the real time granularity
     * is much larger (normal precision is up to tens of millisecond)</em></p>
     *
     * @param clientAddress Local client address
     * @param bb The buffer with data
     * @return How long the buffer should be postponed before sent (in milliseconds)
     *
     * @see NioReactor#NioReactor(long)
     */
    long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb);

    /**
     * Combine this throttler with other one
     * @param other Other throttler
     * @return Combined throttler
     */
    default Throttler combine(Throttler other) {
        return (clientAddress, bb) ->
            Math.max(this.calculateDelayNs(clientAddress, bb), other.calculateDelayNs(clientAddress, bb));
    }

}
