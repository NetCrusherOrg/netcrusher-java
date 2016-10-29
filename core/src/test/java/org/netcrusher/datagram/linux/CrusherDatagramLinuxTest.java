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

public class CrusherDatagramLinuxTest extends AbstractDatagramLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrusherDatagramLinuxTest.class);

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress("127.0.0.1", 50100)
            .withConnectAddress("127.0.0.1", 50101)
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
        loop(DEFAULT_BYTES, DEFAULT_THROUGHPUT, 50100, 50101);
    }

    @Test
    public void direct() throws Exception {
        direct(DEFAULT_BYTES, DEFAULT_THROUGHPUT, 50100, 50101);
    }

    @Test
    public void loopSlower() throws Exception {
        loop(DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT / 10, 50100, 50101);
    }

    @Test
    public void directSlower() throws Exception {
        direct(DEFAULT_BYTES / 10, DEFAULT_THROUGHPUT / 10, 50100, 50101);
    }

    @Test
    public void loopSlowest() throws Exception {
        loop(DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT / 100, 50100, 50101);
    }

    @Test
    public void directSlowest() throws Exception {
        direct(DEFAULT_BYTES / 100, DEFAULT_THROUGHPUT / 100, 50100, 50101);
    }

}
