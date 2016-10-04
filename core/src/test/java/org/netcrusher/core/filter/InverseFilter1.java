package org.netcrusher.core.filter;

import java.nio.ByteBuffer;

public class InverseFilter1 implements ByteBufferFilter {

    public static final ByteBufferFilter INSTANCE = new InverseFilter1();

    public static final ByteBufferFilterFactory FACTORY = (address) -> INSTANCE;

    @Override
    public void filter(ByteBuffer bb) {
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
