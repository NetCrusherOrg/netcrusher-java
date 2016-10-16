package org.netcrusher.core.filter;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Sample filter. Inverse all bytes in bytebuffer as an array
 */
public class InverseFilter1 implements TransformFilter {

    public static final TransformFilter INSTANCE = new InverseFilter1();

    @Override
    public void transform(InetSocketAddress clientAddress, ByteBuffer bb) {
        if (!bb.hasArray()) {
            throw new IllegalArgumentException("Filter works with byte array buffer only");
        }

        final byte[] bytes = bb.array();
        final int offset = bb.arrayOffset() + bb.position();
        final int limit = bb.arrayOffset() + bb.limit();

        for (int i = offset; i < limit; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
    }

}
