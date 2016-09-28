package org.netcrusher.filter;

import java.nio.ByteBuffer;

public class InverseFilter2 implements ByteBufferFilter {

    public static final ByteBufferFilter INSTANCE = new InverseFilter2();

    public static final ByteBufferFilterFactory FACTORY = (address) -> INSTANCE;

    @Override
    public ByteBuffer filter(ByteBuffer bb) {
        for (int i = bb.position(); i < bb.limit(); i++) {
            bb.put(i, (byte) ~bb.get(i));
        }

        return bb;
    }

}
