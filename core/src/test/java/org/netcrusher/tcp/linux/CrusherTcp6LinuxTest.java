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

    private static final String SOCAT6_PROCESSOR =
        SOCAT6 + " - tcp6:[::1]:50100,ignoreeof";

    private static final String SOCAT6_REFLECTOR =
        SOCAT6 + " -b 16384 PIPE tcp6-listen:50101,bind=[::1],reuseaddr";

    private static final String SOCAT6_PRODUCER =
        SOCAT6 + " - tcp6:[::1]:50100";

    private static final String SOCAT6_CONSUMER =
        SOCAT6 + " - tcp6-listen:50101,bind=[::1],reuseaddr";

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress("::1", 50100)
            .withConnectAddress("::1", 50101)
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
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void loopSlower() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR, DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT / 10);
    }

    @Test
    public void loopSlowest() throws Exception {
        loop(SOCAT6_PROCESSOR, SOCAT6_REFLECTOR, DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT / 100);
    }

    @Test
    public void direct() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER, DEFAULT_BYTES, DEFAULT_THROUGHPUT);
    }

    @Test
    public void directSlower() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER, DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT / 10);
    }

    @Test
    public void directSlowest() throws Exception {
        direct(SOCAT6_PRODUCER, SOCAT6_CONSUMER, DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT / 100);
    }
}
