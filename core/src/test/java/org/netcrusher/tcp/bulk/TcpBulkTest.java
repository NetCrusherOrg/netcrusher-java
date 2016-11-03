package org.netcrusher.tcp.bulk;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

public class TcpBulkTest {

    private static final int PORT_SERVER = 10082;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 2 * 1_000_000;

    private static final long SEND_WAIT_MS = 20_000;

    private static final long READ_WAIT_MS = 10_000;

    private TcpBulkServer server;

    @Before
    public void setUp() throws Exception {
        server = new TcpBulkServer(new InetSocketAddress(HOSTNAME, PORT_SERVER), COUNT);
        server.open();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void test() throws Exception {
        final TcpBulkClient client1 = TcpBulkClient.forAddress("EXT", new InetSocketAddress(HOSTNAME, PORT_SERVER), COUNT);
        final byte[] producer1Digest = client1.awaitProducerResult(SEND_WAIT_MS).getDigest();

        Assert.assertEquals(1, server.getClients().size());
        final TcpBulkClient client2 = server.getClients().iterator().next();
        final byte[] producer2Digest = client2.awaitProducerResult(SEND_WAIT_MS).getDigest();

        final byte[] consumer1Digest = client1.awaitConsumerResult(READ_WAIT_MS).getDigest();
        final byte[] consumer2Digest = client2.awaitConsumerResult(READ_WAIT_MS).getDigest();

        client1.close();
        client2.close();

        Assert.assertNotNull(producer1Digest);
        Assert.assertNotNull(producer2Digest);

        Assert.assertArrayEquals(producer1Digest, consumer2Digest);
        Assert.assertArrayEquals(producer2Digest, consumer1Digest);
    }
}
