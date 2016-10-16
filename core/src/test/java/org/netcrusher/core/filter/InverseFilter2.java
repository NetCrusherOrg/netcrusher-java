package org.netcrusher.core.filter;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


/**
 * Sample filter. Inverse all bytes in bytebuffer without knowning it has an array
 */
public class InverseFilter2 implements TransformFilter {

    public static final TransformFilter INSTANCE = new InverseFilter2();

    @Override
    public void transform(InetSocketAddress clientAddress, ByteBuffer bb) {
        for (int i = bb.position(); i < bb.limit(); i++) {
            bb.put(i, (byte) ~bb.get(i));
        }
    }

}
