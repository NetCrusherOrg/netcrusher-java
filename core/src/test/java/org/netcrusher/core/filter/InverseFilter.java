package org.netcrusher.core.filter;

import java.nio.ByteBuffer;

/**
 * Sample filter. Inverse all bytes in bytebuffer as an array
 */
public class InverseFilter implements TransformFilter {

    public static final TransformFilter INSTANCE = new InverseFilter();

    @Override
    public void transform(ByteBuffer bb) {
        if (bb.hasArray()) {
            final byte[] bytes = bb.array();

            final int offset = bb.arrayOffset() + bb.position();
            final int limit = bb.arrayOffset() + bb.limit();

            for (int i = offset; i < limit; i++) {
                bytes[i] = (byte) ~bytes[i];
            }
        } else {
            for (int i = bb.position(); i < bb.limit(); i++) {
                bb.put(i, (byte) ~bb.get(i));
            }
        }
    }

}
