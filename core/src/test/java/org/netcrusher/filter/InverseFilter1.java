package org.netcrusher.filter;

import java.nio.ByteBuffer;

public class InverseFilter1 implements ByteBufferFilter {

    public static final ByteBufferFilter INSTANCE = new InverseFilter1();

    public static final ByteBufferFilterFactory FACTORY = (address) -> INSTANCE;

    @Override
    public void filter(ByteBuffer bb) {
        if (!bb.hasArray()) {
            throw new IllegalArgumentException("Filter works with byte array buffer only");
        }

        final byte[] bytes = bb.array(); // byte array buffer is expected

        for (int i = bb.arrayOffset(), limit = bb.arrayOffset() + bb.limit(); i < limit; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
    }

}
