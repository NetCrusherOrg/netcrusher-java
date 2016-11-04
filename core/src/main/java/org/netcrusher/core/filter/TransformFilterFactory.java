package org.netcrusher.core.filter;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * Transform filter factory
 */
@FunctionalInterface
public interface TransformFilterFactory extends Serializable {

    /**
     * Allocates transform filter for the specified client address
     * @param clientAddress Address of local client socket
     * @return Transform filter instance
     */
    TransformFilter allocate(InetSocketAddress clientAddress);

}
