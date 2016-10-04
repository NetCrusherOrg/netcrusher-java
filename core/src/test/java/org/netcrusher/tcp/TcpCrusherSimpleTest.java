package org.netcrusher.tcp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.NioReactor;

public class TcpCrusherSimpleTest {

    private static final int LISTEN_PORT = 10080;

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress("localhost", LISTEN_PORT)
            .withConnectAddress("google.com", 80)
            .withConnectionTimeoutMs(5000)
            .withBacklog(100)
            .build();
    }

    @After
    public void tearDown() throws Exception {
        if (crusher != null) {
            crusher.close();
        }

        if (reactor != null) {
            reactor.close();
        }
    }

    @Test
    public void testLifeCycle() throws Exception {
        crusher.open();
        Thread.sleep(1000);
        crusher.close();
    }

    @Test
    public void testReopen() throws Exception {
        crusher.open();
        crusher.close();
        crusher.open();
        crusher.close();

        crusher.open();
        Thread.sleep(1000);
        crusher.close();
    }


}