package org.netcrusher.datagram.linux.socat;

import org.junit.Test;

public class DirectDatagramSocat6Test extends AbstractDatagramSocatTest {

    @Test
    public void loop() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT_KBPERSEC);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT_KBPERSEC);
    }

}
