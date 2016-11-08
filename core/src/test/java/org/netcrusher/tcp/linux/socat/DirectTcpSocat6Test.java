package org.netcrusher.tcp.linux.socat;

import org.junit.Test;

public class DirectTcpSocat6Test extends AbstractTcpSocatTest {

    @Test
    public void loop() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_DIRECT, DEFAULT_BYTES, FULL_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_DIRECT, DEFAULT_BYTES, FULL_THROUGHPUT);
    }

}
