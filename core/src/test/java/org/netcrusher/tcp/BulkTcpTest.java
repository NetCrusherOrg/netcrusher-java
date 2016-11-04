package org.netcrusher.tcp;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.tcp.bulk.TcpBulkClient;
import org.netcrusher.tcp.bulk.TcpBulkServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class BulkTcpTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkTcpTest.class);

    private static final int PORT_CRUSHER = 10081;

    private static final int PORT_SERVER = 10082;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 256 * 1024 * 1024;

    private static final long SEND_WAIT_MS = 60_000;

    private static final long READ_WAIT_MS = 30_000;

    private NioReactor reactor;

    private TcpCrusher crusher;

    private TcpBulkServer server;

    @Before
    public void setUp() throws Exception {
        server = new TcpBulkServer(new InetSocketAddress(HOSTNAME, PORT_SERVER), COUNT);
        server.open();

        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, PORT_CRUSHER)
            .withConnectAddress(HOSTNAME, PORT_SERVER)
            .withIncomingTransformFilterFactory((addr) -> TransformFilter.NOOP)
            .withOutgoingTransformFilterFactory((addr) -> TransformFilter.NOOP)
            .withIncomingThrottlerFactory((addr) -> Throttler.NOOP)
            .withOutgoingThrottlerFactory((addr) -> Throttler.NOOP)
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

        if (server != null) {
            server.close();
        }
    }

    @Test
    public void testBulk() throws Exception {
        crusher.freeze();
        Assert.assertTrue(crusher.isFrozen());
        Assert.assertTrue(crusher.isOpen());

        crusher.unfreeze();
        Assert.assertFalse(crusher.isFrozen());
        Assert.assertTrue(crusher.isOpen());

        final InetSocketAddress serverAddress = new InetSocketAddress(HOSTNAME, PORT_CRUSHER);
        try (TcpBulkClient client1 = TcpBulkClient.forAddress("EXT", serverAddress, COUNT)) {
            final byte[] producer1Digest = client1.awaitProducerResult(SEND_WAIT_MS).getDigest();

            Assert.assertEquals(1, server.getClients().size());
            try (TcpBulkClient client2 = server.getClients().iterator().next()) {
                final byte[] producer2Digest = client2.awaitProducerResult(SEND_WAIT_MS).getDigest();

                final byte[] consumer1Digest = client1.awaitConsumerResult(READ_WAIT_MS).getDigest();
                final byte[] consumer2Digest = client2.awaitConsumerResult(READ_WAIT_MS).getDigest();

                Assert.assertEquals(1, crusher.getClientAddresses().size());
                InetSocketAddress clientAddress = crusher.getClientAddresses().iterator().next();
                Assert.assertNotNull(clientAddress);

                RateMeters clientMeters = crusher.getClientByteMeters(clientAddress);
                Assert.assertEquals(COUNT, clientMeters.getReadMeter().getTotalCount());
                Assert.assertEquals(COUNT, clientMeters.getSentMeter().getTotalCount());

                Assert.assertArrayEquals(producer1Digest, consumer2Digest);
                Assert.assertArrayEquals(producer2Digest, consumer1Digest);
            }
        }
    }
}
