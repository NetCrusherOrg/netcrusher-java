package org.netcrusher.core.filter;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class NoopFilter implements TransformFilter {

    public static final TransformFilter INSTANCE = new NoopFilter();

    @Override
    public void transform(InetSocketAddress clientAddress, ByteBuffer bb) {
        // do nothing
    }

}
