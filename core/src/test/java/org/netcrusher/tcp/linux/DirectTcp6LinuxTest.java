package org.netcrusher.tcp.linux;

import org.junit.Test;

public class DirectTcp6LinuxTest extends AbstractTcpLinuxTest {

    private static final String SOCAT6_PROCESSOR =
        SOCAT6 + " - tcp6:[::1]:50100,ignoreeof";

    private static final String SOCAT6_REFLECTOR =
        SOCAT6 + " -b 16384 PIPE tcp6-listen:50100,bind=[::1],reuseaddr";

    private static final String SOCAT6_PRODUCER =
        SOCAT6 + " - tcp6:[::1]:50100";

    private static final String SOCAT6_CONSUMER =
        SOCAT6 + " - tcp6-listen:50100,bind=[::1],reuseaddr";

    @Test
    public void loop() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

}
