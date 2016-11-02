package org.netcrusher.datagram.linux;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.datagram.DatagramCrusher;
import org.netcrusher.datagram.DatagramCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.StandardProtocolFamily;

public class CrusherDatagram6LinuxTest extends AbstractDatagramLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrusherDatagram6LinuxTest.class);

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(ADDR_LOOPBACK6, PORT_DIRECT)
            .withConnectAddress(ADDR_LOOPBACK6, PORT_PROXY)
            .withProtocolFamily(StandardProtocolFamily.INET6)
            .withCreationListener((addr) -> LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters, packetMeters) -> LOGGER.info("Client is deleted <{}>", addr))
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
    public void loop() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_PROXIED, DEFAULT_BYTES, DEFAULT_THROUGHPUT_KBPERSEC);
    }

    @Test
    public void loopSlower() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_PROXIED, DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT_KBPERSEC / 10);
    }

    @Test
    public void loopSlowest() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_PROXIED, DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT_KBPERSEC / 100);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_PROXIED, DEFAULT_BYTES, DEFAULT_THROUGHPUT_KBPERSEC);
    }

    @Test
    public void directSlower() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_PROXIED, DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT_KBPERSEC / 10);
    }

    @Test
    public void directSlowest() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_PROXIED, DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT_KBPERSEC / 100);
    }

}
