package org.netcrusher.filter;

import java.nio.ByteBuffer;

public class CopyFilter implements ByteBufferFilter {

    public static final ByteBufferFilter INSTANCE = new CopyFilter();

    public static final ByteBufferFilterFactory FACTORY = (address) -> INSTANCE;

    @Override
    public ByteBuffer filter(ByteBuffer bb) {
        ByteBuffer copy = ByteBuffer.allocate(bb.limit());
        copy.put(bb);
        copy.flip();
        return copy;
    }

}
