package org.netcrusher.datagram.linux;

import org.junit.Test;

public class DirectDatagram4LinuxTest extends AbstractDatagramLinuxTest {

    private static final String SOCAT4_PROCESSOR =
        SOCAT4 + " - udp4-sendto:127.0.0.1:50100,ignoreeof";

    private static final String SOCAT4_REFLECTOR =
        SOCAT4 + " -b 16384 PIPE udp4-listen:50100,bind=127.0.0.1,reuseaddr";

    private static final String SOCAT4_PRODUCER =
        SOCAT4 + " - udp4-sendto:127.0.0.1:50100";

    private static final String SOCAT4_CONSUMER =
        SOCAT4 + " - udp4-listen:50100,bind=127.0.0.1,reuseaddr";

    @Test
    public void loop() throws Exception {
        loop(SOCAT4_PROCESSOR, SOCAT4_REFLECTOR, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT4_PRODUCER, SOCAT4_CONSUMER, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

}
