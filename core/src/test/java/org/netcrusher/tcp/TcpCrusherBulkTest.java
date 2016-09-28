package org.netcrusher.tcp;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.common.NioReactor;
import org.netcrusher.tcp.bulk.TcpBulkClient;
import org.netcrusher.tcp.bulk.TcpBulkServer;

import java.net.InetSocketAddress;

public class TcpCrusherBulkTest {

    private static final int PORT_CRUSHER = 10081;

    private static final int PORT_SERVER = 10082;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 64 * 1024 * 1024;

    private NioReactor reactor;

    private TcpCrusher crusher;

    private TcpBulkServer server;

    @Before
    public void setUp() throws Exception {
        server = new TcpBulkServer(new InetSocketAddress(HOSTNAME, PORT_SERVER), COUNT);

        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withLocalAddress(HOSTNAME, PORT_CRUSHER)
            .withRemoteAddress(HOSTNAME, PORT_SERVER)
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

        if (server != null) {
            server.close();
        }
    }

    @Test
    public void testCrusher() throws Exception {
        crusher.freeze();
        crusher.unfreeze();

        TcpBulkClient client1 = TcpBulkClient.forAddress(new InetSocketAddress(HOSTNAME, PORT_CRUSHER), COUNT);
        client1.await(20000);

        Assert.assertEquals(1, server.getClients().size());
        TcpBulkClient client2 = server.getClients().iterator().next();
        client2.await(20000);

        Assert.assertNotNull(client1.getRcvDigest());
        Assert.assertNotNull(client2.getRcvDigest());

        Assert.assertArrayEquals(client1.getRcvDigest(), client2.getSndDigest());
        Assert.assertArrayEquals(client2.getRcvDigest(), client1.getSndDigest());

        client1.close();
        client2.close();
    }
}
