package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.datagram.bulk.DatagramBulkReflector;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class EmptyDatagramTest {

    private static final InetSocketAddress CRUSHER_ADDRESS = new InetSocketAddress("127.0.0.1", 10284);

    private static final InetSocketAddress REFLECTOR_ADDRESS = new InetSocketAddress("127.0.0.1", 10285);

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(CRUSHER_ADDRESS)
            .withConnectAddress(REFLECTOR_ADDRESS)
            .buildAndOpen();
    }

    @After
    public void tearDown() throws Exception {
        if (crusher != null) {
            crusher.close();
        }

        if (reactor != null) {
            reactor.close();
        }
    }

    @Test
    public void test() throws Exception {
        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR", REFLECTOR_ADDRESS);
        reflector.open();

        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);

        ByteBuffer bb = ByteBuffer.allocate(100);

        try {
            // sent
            bb.clear();
            bb.flip();
            int sent = channel.send(bb, CRUSHER_ADDRESS);
            Assert.assertEquals(0, sent);

            // check
            Thread.sleep(500);

            RateMeters innerByteMeters = crusher.getInnerByteMeters();
            Assert.assertEquals(0, innerByteMeters.getReadMeter().getTotalCount());
            Assert.assertEquals(0, innerByteMeters.getSentMeter().getTotalCount());

            RateMeters innerPacketMeters = crusher.getInnerPacketMeters();
            Assert.assertEquals(1, innerPacketMeters.getReadMeter().getTotalCount());
            Assert.assertEquals(1, innerPacketMeters.getSentMeter().getTotalCount());

            // read
            bb.clear();
            InetSocketAddress address = (InetSocketAddress) channel.receive(bb);
            Assert.assertNotNull(address);
            Assert.assertEquals(CRUSHER_ADDRESS, address);
            Assert.assertEquals(0, bb.position());
        } finally {
            channel.close();
            reflector.close();
        }
    }

    @Test
    public void testBlockSockets() throws Exception {
        DatagramChannel channel1 = DatagramChannel.open();
        channel1.configureBlocking(true);
        // No empty datagram for connected socket
        // https://bugs.openjdk.java.net/browse/JDK-8013175
        // channel1.connect(bindAddress);

        DatagramChannel channel2 = DatagramChannel.open();
        channel2.configureBlocking(true);
        channel2.bind(REFLECTOR_ADDRESS);

        ByteBuffer bb = ByteBuffer.allocate(0);
        bb.clear();

        try {
            bb.flip();
            int sent = channel1.send(bb, REFLECTOR_ADDRESS);
            Assert.assertEquals(0, sent);

            Thread.sleep(100);

            bb.clear();
            InetSocketAddress address = (InetSocketAddress) channel2.receive(bb);
            Assert.assertNotNull(address);
            Assert.assertEquals(0, bb.position());
        } finally {
            channel2.close();
            channel1.close();
        }
    }

    @Test
    public void testNoCrusher() throws Exception {
        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR", REFLECTOR_ADDRESS);
        reflector.open();

        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);
        // No empty datagram for connected socket
        // https://bugs.openjdk.java.net/browse/JDK-8013175
        // channel.connect(reflectorAddress);

        ByteBuffer bb = ByteBuffer.allocate(0);

        try {
            // sent
            bb.clear();
            bb.flip();
            int sent = channel.send(bb, REFLECTOR_ADDRESS);
            Assert.assertEquals(0, sent);

            // read
            bb.clear();
            InetSocketAddress address = (InetSocketAddress) channel.receive(bb);
            Assert.assertNotNull(address);
            Assert.assertEquals(REFLECTOR_ADDRESS, address);
            Assert.assertEquals(0, bb.position());
        } finally {
            channel.close();
            reflector.close();
        }
    }
}
