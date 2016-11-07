package org.netcrusher.core.filter;

import java.nio.ByteBuffer;

public class LengthPassFilter implements PassFilter {

    @Override
    public boolean check(ByteBuffer bb) {
        // only small packets will pass
        return bb.remaining() < 100;
    }
}
