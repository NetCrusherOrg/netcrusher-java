package org.netcrusher.tcp.linux.iperf;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrusherTcpIperf4Test extends AbstractTcpIperfTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrusherTcpIperf4Test.class);

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor(10);

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(ADDR_LOOPBACK4, PORT_CRUSHER)
            .withConnectAddress(ADDR_LOOPBACK4, PORT_IPERF)
            .withCreationListener((addr) ->
                LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters) ->
                LOGGER.info("Client is deleted <{}>", addr))
            .buildAndOpen();
    }

    @After
    public void tearDown() throws Exception {
        if (crusher != null) {
            crusher.close();
            Assert.assertFalse(crusher.isOpen());
        }

        if (reactor != null) {
            reactor.close();
            Assert.assertFalse(reactor.isOpen());
        }
    }

    @Test
    public void test() throws Exception {
        loop(IPERF_SERVER, IPERF4_CLIENT_PROXIED);
    }
}
