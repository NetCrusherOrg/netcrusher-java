package org.netcrusher.tcp.throttling;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.rate.ByteRateThrottler;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.netcrusher.tcp.bulk.TcpBulkClient;
import org.netcrusher.tcp.bulk.TcpBulkResult;
import org.netcrusher.tcp.bulk.TcpBulkServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class RateThrottlingTcpTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateThrottlingTcpTest.class);

    private static final int PORT_CRUSHER = 10081;

    private static final int PORT_SERVER = 10082;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 32 * 1000 * 1000;

    private static final long SEND_WAIT_MS = 60_000;

    private static final long READ_WAIT_MS = 30_000;

    private static final int INCOMING_BYTES_PER_SEC = 2_000_000;

    private static final int OUTGOING_BYTES_PER_SEC = 4_000_000;

    private static final double RATE_PRECISION = 0.05;

    private NioReactor reactor;

    private TcpCrusher crusher;

    private TcpBulkServer server;

    @Before
    public void setUp() throws Exception {
        server = new TcpBulkServer(new InetSocketAddress(HOSTNAME, PORT_SERVER), COUNT);
        server.open();

        reactor = new NioReactor(10);

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, PORT_CRUSHER)
            .withConnectAddress(HOSTNAME, PORT_SERVER)
            .withIncomingThrottlerFactory((addr) ->
                new ByteRateThrottler(INCOMING_BYTES_PER_SEC, 1, TimeUnit.SECONDS))
            .withOutgoingThrottlerFactory((addr) ->
                new ByteRateThrottler(OUTGOING_BYTES_PER_SEC, 1, TimeUnit.SECONDS))
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
    public void testRate() throws Exception {
        final InetSocketAddress serverAddress = new InetSocketAddress(HOSTNAME, PORT_CRUSHER);

        try (TcpBulkClient client1 = TcpBulkClient.forAddress("EXT", serverAddress, COUNT)) {
            final TcpBulkResult producer1Result = client1.awaitProducerResult(SEND_WAIT_MS);

            Assert.assertEquals(1, server.getClients().size());
            try (TcpBulkClient client2 = server.getClients().iterator().next()) {
                final TcpBulkResult producer2Result = client2.awaitProducerResult(SEND_WAIT_MS);

                final TcpBulkResult consumer1Result = client1.awaitConsumerResult(READ_WAIT_MS);
                final TcpBulkResult consumer2Result = client2.awaitConsumerResult(READ_WAIT_MS);

                Assert.assertArrayEquals(producer1Result.getDigest(), consumer2Result.getDigest());
                Assert.assertArrayEquals(producer2Result.getDigest(), consumer1Result.getDigest());

                double incomingRate = 1000.0 * consumer1Result.getBytes() / consumer1Result.getElapsedMs();
                LOGGER.info("Incoming rate is {} bytes/sec", incomingRate);
                Assert.assertEquals(INCOMING_BYTES_PER_SEC, incomingRate, INCOMING_BYTES_PER_SEC * RATE_PRECISION);

                double outgoingRate = 1000.0 * consumer2Result.getBytes() / consumer2Result.getElapsedMs();
                LOGGER.info("Outgoing rate is {} bytes/sec", outgoingRate);
                Assert.assertEquals(OUTGOING_BYTES_PER_SEC, outgoingRate, OUTGOING_BYTES_PER_SEC * RATE_PRECISION);
            }
        }
    }
}
