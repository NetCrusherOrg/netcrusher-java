package org.netcrusher.tcp.linux.socat.throttling;

public class CrusherThottling10MTcpSocatTest extends AbstractThottlingTcpSocatTest {

    private static final int BYTES_PER_SEC = 10_000_000;

    private static final int DURATION_SEC = 20;

    public CrusherThottling10MTcpSocatTest() {
        super(BYTES_PER_SEC, DURATION_SEC);
    }
}
