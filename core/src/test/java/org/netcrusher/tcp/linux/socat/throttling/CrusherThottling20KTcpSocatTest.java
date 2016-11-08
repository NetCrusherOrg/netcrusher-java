package org.netcrusher.tcp.linux.socat.throttling;

public class CrusherThottling20KTcpSocatTest extends AbstractThottlingTcpSocatTest {

    private static final int BYTES_PER_SEC = 20_000;

    private static final int DURATION_SEC = 20;

    public CrusherThottling20KTcpSocatTest() {
        super(BYTES_PER_SEC, DURATION_SEC);
    }

}
