package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.filter.InverseFilter;
import org.netcrusher.core.filter.NoopFilter;
import org.netcrusher.core.filter.TransformFilters;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.NoopThrottler;
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

    private static final long COUNT = 16 * 1_000_000;

    private static final long SEND_WAIT_MS = 60_000;

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
            .withIncomingTransformFilter(TransformFilters.all(NoopFilter.INSTANCE, InverseFilter.INSTANCE))
            .withIncomingPassFilter((addr, bb) -> true)
            .withIncomingThrottler(NoopThrottler.INSTANCE)
            .withOutgoingTransformFilter(TransformFilters.all(InverseFilter.INSTANCE, NoopFilter.INSTANCE))
            .withOutgoingPassFilter((addr, bb) -> true)
            .withOutgoingThrottler(NoopThrottler.INSTANCE)
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
            barrier);

        reflector.open();
        client.open();

        final byte[] producerDigest = client.awaitProducerDigest(SEND_WAIT_MS);
        final byte[] consumerDigest = client.awaitConsumerDigest(READ_WAIT_MS);

        try {
            Assert.assertEquals(1, crusher.getClientAddresses().size());
            InetSocketAddress clientAddress = crusher.getClientAddresses().iterator().next();
            Assert.assertNotNull(clientAddress);

            RateMeters innerByteMeters = crusher.getInnerByteMeters();
            Assert.assertEquals(COUNT, innerByteMeters.getReadMeter().getTotalCount());
            Assert.assertEquals(COUNT, innerByteMeters.getSentMeter().getTotalCount());

            RateMeters outerByteMeters = crusher.getClientByteMeters(clientAddress);
            Assert.assertEquals(COUNT, outerByteMeters.getReadMeter().getTotalCount());
            Assert.assertEquals(COUNT, outerByteMeters.getSentMeter().getTotalCount());

            long minExpectedDatagrams = (COUNT + DatagramBulkClient.MAX_DATAGRAM_SIZE - 1) / DatagramBulkClient.MAX_DATAGRAM_SIZE;

            RateMeters innerPacketMeters = crusher.getInnerPacketMeters();
            Assert.assertTrue(minExpectedDatagrams <= innerPacketMeters.getReadMeter().getTotalCount());
            Assert.assertTrue(minExpectedDatagrams <= innerPacketMeters.getSentMeter().getTotalCount());

            RateMeters outerPacketMeters = crusher.getClientPacketMeters(clientAddress);
            Assert.assertTrue(minExpectedDatagrams <= outerPacketMeters.getReadMeter().getTotalCount());
            Assert.assertTrue(minExpectedDatagrams <= outerPacketMeters.getSentMeter().getTotalCount());
        } finally {
            client.close();
            reflector.close();
        }

        Assert.assertNotNull(producerDigest);
        Assert.assertNotNull(consumerDigest);
        Assert.assertArrayEquals(producerDigest, consumerDigest);
    }
}
