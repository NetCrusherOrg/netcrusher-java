package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.filter.InverseFilter;
import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.filter.TransformFilters;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.nio.NioUtils;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.datagram.bulk.DatagramBulkClient;
import org.netcrusher.datagram.bulk.DatagramBulkReflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CyclicBarrier;

public class BulkDatagramTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkDatagramTest.class);

    private static final int CLIENT_PORT = 10182;

    private static final int CRUSHER_PORT = 10183;

    private static final int REFLECTOR_PORT = 10184;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 10_000;

    private static final long SEND_WAIT_MS = 120_000;

    private static final long READ_WAIT_MS = 30_000;

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, CRUSHER_PORT)
            .withConnectAddress(HOSTNAME, REFLECTOR_PORT)
            .withIncomingTransformFilterFactory(
                TransformFilters.all((addr) -> TransformFilter.NOOP, (addr) -> InverseFilter.INSTANCE))
            .withOutgoingTransformFilterFactory(
                TransformFilters.all((addr) -> InverseFilter.INSTANCE, (addr) -> TransformFilter.NOOP))
            .withIncomingPassFilterFactory((addr) -> PassFilter.NOOP)
            .withOutgoingPassFilterFactory((addr) -> PassFilter.NOOP)
            .withIncomingGlobalThrottler(Throttler.NOOP)
            .withOutgoingThrottlerFactory((addr) -> Throttler.NOOP)
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
    public void test() throws Exception {
        crusher.freeze();
        Assert.assertTrue(crusher.isFrozen());
        Assert.assertTrue(crusher.isOpen());

        crusher.unfreeze();
        Assert.assertFalse(crusher.isFrozen());
        Assert.assertTrue(crusher.isOpen());

        CyclicBarrier barrier = new CyclicBarrier(3);

        DatagramBulkClient client = new DatagramBulkClient("CLIENT",
            new InetSocketAddress(HOSTNAME, CLIENT_PORT),
            new InetSocketAddress(HOSTNAME, CRUSHER_PORT),
            COUNT,
            barrier,
            barrier);

        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR",
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT),
            COUNT,
            barrier);

        reflector.open();
        client.open();

        try {
            final byte[] producerDigest = client.awaitProducerResult(SEND_WAIT_MS).getDigest();
            final byte[] consumerDigest = client.awaitConsumerResult(READ_WAIT_MS).getDigest();

            reflector.awaitReflectorResult(READ_WAIT_MS).getDigest();

            Assert.assertEquals(1, crusher.getClientAddresses().size());
            InetSocketAddress clientAddress = crusher.getClientAddresses().iterator().next();
            Assert.assertNotNull(clientAddress);

            RateMeters innerByteMeters = crusher.getInnerByteMeters();
            Assert.assertTrue(innerByteMeters.getReadMeter().getTotalCount() > 0);
            Assert.assertTrue(innerByteMeters.getSentMeter().getTotalCount() > 0);

            RateMeters outerByteMeters = crusher.getClientByteMeters(clientAddress);
            Assert.assertTrue(outerByteMeters.getReadMeter().getTotalCount() > 0);
            Assert.assertTrue(outerByteMeters.getSentMeter().getTotalCount() > 0);

            RateMeters innerPacketMeters = crusher.getInnerPacketMeters();
            Assert.assertEquals(COUNT, innerPacketMeters.getReadMeter().getTotalCount());
            Assert.assertEquals(COUNT, innerPacketMeters.getSentMeter().getTotalCount());

            RateMeters outerPacketMeters = crusher.getClientPacketMeters(clientAddress);
            Assert.assertEquals(COUNT, outerPacketMeters.getReadMeter().getTotalCount());
            Assert.assertEquals(COUNT, outerPacketMeters.getSentMeter().getTotalCount());

            Assert.assertArrayEquals(producerDigest, consumerDigest);
        } finally {
            NioUtils.close(client);
            NioUtils.close(reflector);
        }
    }
}
