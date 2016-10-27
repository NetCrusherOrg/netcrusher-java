package org.netcrusher.datagram.linux;

import org.junit.Test;

public class DirectDatagramLinuxTest extends AbstractDatagramLinuxTest {

    @Test
    public void test() throws Exception {
        session(DEFAULT_BYTES, 50100, 50100);
    }

}
