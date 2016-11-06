package org.netcrusher.tcp.linux.throttling;

public class CrusherThottling20KTcpLinuxTest extends AbstractThottlingTcpLinuxTest {

    private static final int BYTES_PER_SEC = 20_000;

    private static final int DURATION_SEC = 20;

    public CrusherThottling20KTcpLinuxTest() {
        super(BYTES_PER_SEC, DURATION_SEC);
    }

}
