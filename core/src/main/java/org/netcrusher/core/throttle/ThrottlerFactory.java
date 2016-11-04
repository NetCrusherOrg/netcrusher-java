package org.netcrusher.core.throttle;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * Throttler factory
 */
@FunctionalInterface
public interface ThrottlerFactory extends Serializable {

    /**
     * Allocates throttler for the specified client address
     * @param clientAddress Local client address
     * @return Throttler instance
     */
    Throttler allocate(InetSocketAddress clientAddress);

}
