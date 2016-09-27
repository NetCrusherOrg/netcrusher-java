package org.netcrusher.filter;

import java.net.InetSocketAddress;

public interface ByteBufferFilterFactory {

    /**
     * Creates a filter for a specified address
     * @param clientAddress Local client address
     * @return Byte filter instance
     */
    ByteBufferFilter create(InetSocketAddress clientAddress);

}
