package org.netcrusher.core.filter;

import java.nio.ByteBuffer;

/**
 * Datagram filter
 */
@FunctionalInterface
public interface PassFilter {

    PassFilter NOOP = (bb) -> true;

    /**
     * <p>Callback that determines if the buffer should be sent. Filter also is allowed to modify the buffer
     * as TrasformFilter does.</p>
     * <p><em>Verify that both bb.position() and bb.limit() are properly set after method returns</em></p>
     * @param bb Input byte buffer with position set to 0 and limit set to buffer size
     * @return Return true if buffer (datagram) should be sent
     * @see TransformFilter
     */
    boolean check(ByteBuffer bb);

}
