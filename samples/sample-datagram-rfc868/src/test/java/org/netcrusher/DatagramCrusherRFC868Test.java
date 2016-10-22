package org.netcrusher;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.filter.LoggingFilter;
import org.netcrusher.core.filter.LoggingFilterLevel;
import org.netcrusher.core.meter.RateMeters;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.datagram.DatagramCrusher;
import org.netcrusher.datagram.DatagramCrusherBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class DatagramCrusherRFC868Test {

    private static final InetSocketAddress LOCAL_ADDRESS = new InetSocketAddress("localhost", 10188);

    private static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("time-nw.nist.gov", 37);

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
                .withReactor(reactor)
                .withBindAddress(LOCAL_ADDRESS)
                .withConnectAddress(REMOTE_ADDRESS)
                .withIncomingTransformFilter(new LoggingFilter("incoming", LoggingFilterLevel.DEBUG))
                .withOutgoingTransformFilter(new LoggingFilter("outgoing", LoggingFilterLevel.DEBUG))
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
    public void testRFC868() throws Exception {
        check();

        Assert.assertEquals(1, crusher.getClientAddresses().size());

        InetSocketAddress clientAddress = crusher.getClientAddresses().iterator().next();
        Assert.assertNotNull(clientAddress);

        RateMeters packetMeters = crusher.getClientPacketMeters(clientAddress);
        Assert.assertEquals(1, packetMeters.getSentMeter().getTotalCount());
        Assert.assertEquals(1, packetMeters.getReadMeter().getTotalCount());

        RateMeters byteMeters = crusher.getClientByteMeters(clientAddress);
        Assert.assertEquals(1, byteMeters.getSentMeter().getTotalCount());
        Assert.assertEquals(4, byteMeters.getReadMeter().getTotalCount());

        crusher.reopen();

        check();

        crusher.freeze();
        crusher.unfreeze();

        check();
    }

    private void check() throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            buffer.order(ByteOrder.BIG_ENDIAN);

            buffer.clear();
            buffer.put((byte) 0xFF);

            buffer.flip();
            channel.send(buffer, LOCAL_ADDRESS);

            buffer.clear();
            channel.receive(buffer);

            buffer.flip();
            long seconds = Integer.toUnsignedLong(buffer.getInt());

            Calendar calendar = new GregorianCalendar(1900, Calendar.JANUARY, 1, 0, 0, 0);
            calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
            long timeMs = calendar.getTimeInMillis() + seconds * 1000;

            Assert.assertTrue(Math.abs(System.currentTimeMillis() - timeMs) < 5000);
        }
    }
}