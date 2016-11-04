package org.netcrusher.tcp.linux.throttling;

public class CrusherThottling10MTcpLinuxTest extends AbstractThottlingTcpLinuxTest {

    private static final int BYTES_PER_SEC = 10_000_000;

    private static final int DURATION_SEC = 5;

    public CrusherThottling10MTcpLinuxTest() {
        super(BYTES_PER_SEC, DURATION_SEC);
    }
}
