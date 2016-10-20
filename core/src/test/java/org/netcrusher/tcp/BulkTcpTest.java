package org.netcrusher.tcp;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.filter.NoopFilter;
import org.netcrusher.core.throttle.NoopThrottler;
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

    private NioReactor reactor;

    private TcpCrusher crusher;

    private TcpBulkServer server;

    @Before
    public void setUp() throws Exception {
        server = new TcpBulkServer(new InetSocketAddress(HOSTNAME, PORT_SERVER), COUNT);

        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, PORT_CRUSHER)
            .withConnectAddress(HOSTNAME, PORT_SERVER)
            .withIncomingTransformFilter(NoopFilter.INSTANCE)
            .withIncomingThrottler(NoopThrottler.INSTANCE)
            .withOutgoingTransformFilter(NoopFilter.INSTANCE)
            .withOutgoingThrottler(NoopThrottler.INSTANCE)
            .withCreationListener((pair) -> LOGGER.info("Pair is created for <{}>", pair.getClientAddress()))
            .withDeletionListener((pair) -> LOGGER.info("Pair is deleted for <{}>", pair.getClientAddress()))
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
    public void testCrusher() throws Exception {
        crusher.freeze();
        crusher.unfreeze();

        TcpBulkClient client1 = TcpBulkClient.forAddress("EXT", new InetSocketAddress(HOSTNAME, PORT_CRUSHER), COUNT);
        client1.await(20000);

        Assert.assertEquals(1, server.getClients().size());
        TcpBulkClient client2 = server.getClients().iterator().next();
        client2.await(20000);

        Assert.assertEquals(1, crusher.getPairs().size());
        TcpPair pair = crusher.getPairs().iterator().next();
        Assert.assertNotNull(pair);
        Assert.assertNotNull(pair.getClientAddress());
        Assert.assertEquals(COUNT, pair.getInnerTransfer().getReadMeter().getTotalCount());
        Assert.assertEquals(COUNT, pair.getInnerTransfer().getSentMeter().getTotalCount());
        Assert.assertEquals(COUNT, pair.getOuterTransfer().getReadMeter().getTotalCount());
        Assert.assertEquals(COUNT, pair.getOuterTransfer().getSentMeter().getTotalCount());

        client1.close();
        client2.close();

        Assert.assertNotNull(client1.getRcvDigest());
        Assert.assertNotNull(client2.getRcvDigest());

        Assert.assertArrayEquals(client1.getRcvDigest(), client2.getSndDigest());
        Assert.assertArrayEquals(client2.getRcvDigest(), client1.getSndDigest());
    }
}
