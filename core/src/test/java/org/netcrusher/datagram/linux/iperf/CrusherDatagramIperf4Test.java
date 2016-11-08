package org.netcrusher.datagram.linux.iperf;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.datagram.DatagramCrusher;
import org.netcrusher.datagram.DatagramCrusherBuilder;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrusherDatagramIperf4Test extends AbstractDatagramIperfTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrusherDatagramIperf4Test.class);

    private NioReactor reactor;

    private DatagramCrusher datagramCrusher;

    private TcpCrusher tcpCrusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor(10);

        datagramCrusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(ADDR_LOOPBACK4, PORT_CRUSHER)
            .withConnectAddress(ADDR_LOOPBACK4, PORT_IPERF)
            .withCreationListener((addr) ->
                LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters, packetMeters) ->
                LOGGER.info("Client is deleted <{}>", addr))
            .buildAndOpen();

        // for iperf3 TCP control channel on the same port
        tcpCrusher = TcpCrusherBuilder.builder()
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
        if (datagramCrusher != null) {
            datagramCrusher.close();
            Assert.assertFalse(datagramCrusher.isOpen());
        }

        if (tcpCrusher != null) {
            tcpCrusher.close();
            Assert.assertFalse(tcpCrusher.isOpen());
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
