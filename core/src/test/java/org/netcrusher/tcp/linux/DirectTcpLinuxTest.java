package org.netcrusher.tcp.linux;

import org.junit.Test;

public class DirectTcpLinuxTest extends AbstractTcpLinuxTest {

    @Test
    public void test() throws Exception {
        loop(DEFAULT_BYTES, 50100, 50100);
    }

}
