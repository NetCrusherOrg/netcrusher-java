package org.netcrusher.datagram.linux;

import org.junit.Test;

public class DirectDatagram6LinuxTest extends AbstractDatagramLinuxTest {

    private static final String SOCAT4_PROCESSOR =
        SOCAT6 + " - udp6-sendto:[::1]:50100,ignoreeof";

    private static final String SOCAT4_REFLECTOR =
        SOCAT6 + " -b 16384 PIPE udp6-listen:50100,bind=[::1],reuseaddr";

    private static final String SOCAT4_PRODUCER =
        SOCAT6 + " - udp6-sendto:[::1]:50100";

    private static final String SOCAT4_CONSUMER =
        SOCAT6 + " - udp6-listen:50100,bind=[::1],reuseaddr";

    @Test
    public void loop() throws Exception {
        loop(SOCAT4_PROCESSOR, SOCAT4_REFLECTOR, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT4_PRODUCER, SOCAT4_CONSUMER, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

}
