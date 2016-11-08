package org.netcrusher.tcp.linux.socat;

import org.junit.Test;

public class DirectTcpSocat4Test extends AbstractTcpSocatTest {

    @Test
    public void loop() throws Exception {
        loop(SOCAT4_PROCESSOR, SOCAT4_REFLECTOR_DIRECT, DEFAULT_BYTES, FULL_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT4_PRODUCER, SOCAT4_CONSUMER_DIRECT, DEFAULT_BYTES, FULL_THROUGHPUT);
    }

}
