package org.netcrusher.tcp.linux.iperf;

import org.junit.Test;

public class DirectTcpIperf4Test extends AbstractTcpIperfTest {

    @Test
    public void test() throws Exception {
        loop(IPERF_SERVER, IPERF4_CLIENT_DIRECT);
    }
}
