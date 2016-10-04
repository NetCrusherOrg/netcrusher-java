package org.netcrusher.core.filter;

import java.nio.ByteBuffer;

public class NoopFilter implements ByteBufferFilter {

    public static final ByteBufferFilter INSTANCE = new NoopFilter();

    public static final ByteBufferFilterFactory FACTORY = (address) -> INSTANCE;

    @Override
    public void filter(ByteBuffer bb) {
        // no op
    }

}
