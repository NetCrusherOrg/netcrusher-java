package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.filter.InverseFilter1;
import org.netcrusher.core.filter.InverseFilter2;
import org.netcrusher.core.filter.NoopFilter;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.NoopThrottler;
import org.netcrusher.datagram.bulk.DatagramBulkClient;
import org.netcrusher.datagram.bulk.DatagramBulkReflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class BulkDatagramTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkDatagramTest.class);

    private static final int CLIENT_PORT = 10182;

    private static final int CRUSHER_PORT = 10183;

    private static final int REFLECTOR_PORT = 10184;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 16 * 1_000_000;

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, CRUSHER_PORT)
            .withConnectAddress(HOSTNAME, REFLECTOR_PORT)
            .withIncomingTransformFilter(NoopFilter.INSTANCE.then(InverseFilter2.INSTANCE))
            .withIncomingPassFilter((addr, bb) -> true)
            .withIncomingThrottler(NoopThrottler.INSTANCE)
            .withOutgoingTransformFilter(InverseFilter1.INSTANCE.then(NoopFilter.INSTANCE))
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
        crusher.unfreeze();

        DatagramBulkClient client = new DatagramBulkClient("CLIENT",
            new InetSocketAddress(HOSTNAME, CLIENT_PORT),
            new InetSocketAddress(HOSTNAME, CRUSHER_PORT),
            COUNT);
        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR",
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT));

        reflector.open();
        client.open();

        client.await(60000);

        long expectedDatagrams = (COUNT + DatagramBulkClient.SND_BUFFER_SIZE - 1) / DatagramBulkClient.SND_BUFFER_SIZE;

        RateMeters innerByteMeters = crusher.getInnerByteMeters();
        Assert.assertEquals(COUNT, innerByteMeters.getReadMeter().getTotalCount());
        Assert.assertEquals(COUNT, innerByteMeters.getSentMeter().getTotalCount());

        RateMeters innerPacketMeters = crusher.getInnerPacketMeters();
        Assert.assertTrue(expectedDatagrams <= innerPacketMeters.getReadMeter().getTotalCount());
        Assert.assertTrue(expectedDatagrams <= innerPacketMeters.getSentMeter().getTotalCount());

        Assert.assertEquals(1, crusher.getClientAddresses().size());
        InetSocketAddress clientAddress = crusher.getClientAddresses().iterator().next();
        Assert.assertNotNull(clientAddress);

        RateMeters outerByteMeters = crusher.getClientByteMeters(clientAddress);
        Assert.assertEquals(COUNT, outerByteMeters.getReadMeter().getTotalCount());
        Assert.assertEquals(COUNT, outerByteMeters.getSentMeter().getTotalCount());

        RateMeters outerPacketMeters = crusher.getClientPacketMeters(clientAddress);
        Assert.assertTrue(expectedDatagrams <= outerPacketMeters.getReadMeter().getTotalCount());
        Assert.assertTrue(expectedDatagrams <= outerPacketMeters.getSentMeter().getTotalCount());

        client.close();
        reflector.close();

        Assert.assertNotNull(client.getRcvDigest());

        Assert.assertArrayEquals(client.getRcvDigest(), client.getSndDigest());
    }
}
