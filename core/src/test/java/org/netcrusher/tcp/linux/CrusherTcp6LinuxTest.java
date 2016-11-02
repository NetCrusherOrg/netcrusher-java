package org.netcrusher.tcp.linux;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrusherTcp6LinuxTest extends AbstractTcpLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrusherTcp6LinuxTest.class);

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(ADDR_LOOPBACK6, PORT_DIRECT)
            .withConnectAddress(ADDR_LOOPBACK6, PORT_PROXY)
            .withCreationListener((addr) -> LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters) -> LOGGER.info("Client is deleted <{}>", addr))
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
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_PROXIED, DEFAULT_BYTES, FULL_THROUGHPUT);
    }

    @Test
    public void loopSlower() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_PROXIED, DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT / 10);
    }

    @Test
    public void loopSlowest() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR_PROXIED, DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT / 100);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_PROXIED, DEFAULT_BYTES, FULL_THROUGHPUT);
    }

    @Test
    public void directSlower() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_PROXIED, DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT / 10);
    }

    @Test
    public void directSlowest() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER_PROXIED, DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT / 100);
    }
}
