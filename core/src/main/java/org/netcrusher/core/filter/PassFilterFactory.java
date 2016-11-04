package org.netcrusher.core.filter;

import java.net.InetSocketAddress;

/**
 * Datagram filter factory
 */
@FunctionalInterface
public interface PassFilterFactory {

    /**
     * Allocates pass filter for the specified client address
     * @param clientAddress Address of local client socket
     * @return Pass filter instance
     */
    PassFilter allocate(InetSocketAddress clientAddress);

}
