package org.netcrusher.datagram.linux.iperf;

import org.junit.Test;

public class DirectDatagramIperf4Test extends AbstractDatagramIperfTest {

    @Test
    public void test() throws Exception {
        loop(IPERF_SERVER, IPERF4_CLIENT_DIRECT);
    }
}
