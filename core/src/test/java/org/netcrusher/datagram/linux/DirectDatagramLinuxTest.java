package org.netcrusher.datagram.linux;

import org.junit.Test;

public class DirectDatagramLinuxTest extends AbstractDatagramLinuxTest {

    @Test
    public void loop() throws Exception {
        loop(DEFAULT_BYTES, DEFAULT_THROUGHPUT, 50100, 50100);
    }

    @Test
    public void direct() throws Exception {
        direct(DEFAULT_BYTES, DEFAULT_THROUGHPUT, 50100, 50100);
    }

}
