package org.netcrusher.datagram.linux;

import org.junit.Test;

public class DirectDatagram6LinuxTest extends AbstractDatagramLinuxTest {

    @Test
    public void loop() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

}
