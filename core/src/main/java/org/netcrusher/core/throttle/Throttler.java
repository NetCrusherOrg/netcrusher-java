package org.netcrusher.core.throttle;

import org.netcrusher.core.NioReactor;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface Throttler {

    long NO_DELAY = 0;

    /**
     * <p>Calculate delay for the buffer. Return 0 for no delay.</p>
     *
     * <p><em>Although the returned delay has nanosecond precision
     * real time granularity is much bigger (normal precision is up to tens of millisecond)</em></p>
     * @param clientAddress Local client address
     * @param bb The buffer with data
     * @return Delay for the buffer in nanoseconds
     * @see NioReactor#NioReactor(long)
     * @see Throttler#NO_DELAY
     */
    long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb);

}
