package org.netcrusher.core.filter;

import java.net.InetSocketAddress;

/**
 * Transform filter factory
 */
@FunctionalInterface
public interface TransformFilterFactory {

    /**
     * Allocates transform filter for the specified client address
     * @param clientAddress Address of local client socket
     * @return Transform filter instance
     */
    TransformFilter allocate(InetSocketAddress clientAddress);

}
