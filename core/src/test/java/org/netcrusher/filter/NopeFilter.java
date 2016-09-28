package org.netcrusher.filter;

import java.nio.ByteBuffer;

public class NopeFilter implements ByteBufferFilter {

    public static final ByteBufferFilter INSTANCE = new NopeFilter();

    public static final ByteBufferFilterFactory FACTORY = (address) -> INSTANCE;

    @Override
    public ByteBuffer filter(ByteBuffer bb) {
        return bb;
    }

}
