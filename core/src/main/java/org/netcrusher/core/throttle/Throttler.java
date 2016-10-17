package org.netcrusher.core.throttle;

import org.netcrusher.core.NioReactor;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface Throttler {

    /**
     * A constant for no delay
     */
    long NO_DELAY = 0;

    /**
     * <p>Calculate delay for the buffer. Return NO_DELAY if the buffer should be sent immediately.</p>
     *
     * <p><em>Although the returned delay has nanosecond precision
     * real time granularity is much bigger (normal precision is up to tens of millisecond)</em></p>
     * @param clientAddress Local client address
     * @param bb The buffer with data
     * @return How long the buffer should be postponed before sent (in nanoseconds)
     * @see NioReactor#NioReactor(long)
     * @see Throttler#NO_DELAY
     */
    long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb);

}
