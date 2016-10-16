package org.netcrusher.core.throttle;

import java.nio.ByteBuffer;

public interface Throttler {

    long calculateDelayNs(ByteBuffer bb);

}
