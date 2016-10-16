package org.netcrusher.core.throttle;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface Throttler {

    long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb);

}
