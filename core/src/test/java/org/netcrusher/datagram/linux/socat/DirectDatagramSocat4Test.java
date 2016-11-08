package org.netcrusher.datagram.linux.socat;

import org.junit.Test;

public class DirectDatagramSocat4Test extends AbstractDatagramSocatTest {

    @Test
    public void loop() throws Exception {
        loop(SOCAT4_PROCESSOR, SOCAT4_REFLECTOR_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT_KBPERSEC);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT4_PRODUCER, SOCAT4_CONSUMER_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT_KBPERSEC);
    }

}
