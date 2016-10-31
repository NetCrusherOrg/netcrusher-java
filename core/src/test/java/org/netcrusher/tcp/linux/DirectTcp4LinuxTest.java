package org.netcrusher.tcp.linux;

import org.junit.Test;

public class DirectTcp4LinuxTest extends AbstractTcpLinuxTest {

    @Test
    public void loop() throws Exception {
        loop(SOCAT4_PROCESSOR, SOCAT4_REFLECTOR_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT4_PRODUCER, SOCAT4_CONSUMER_DIRECT, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

}
