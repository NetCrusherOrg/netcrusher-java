package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.common.NioReactor;
import org.netcrusher.datagram.bulk.DatagramBulkClient;

import java.net.InetSocketAddress;

public class DatagramCrusherBulkTest {

    private static final int CLIENT_PORT1 = 10082;

    private static final int CRUSHER_PORT1 = 10083;

    private static final int CLIENT_PORT2 = 10084;

    private static final int CRUSHER_PORT2 = 10085;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 64 * 1024 * 1024;

    private NioReactor reactor;

    private DatagramCrusher crusher1;

    private DatagramCrusher crusher2;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher1 = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withLocalAddress(HOSTNAME, CRUSHER_PORT1)
            .withRemoteAddress(HOSTNAME, CLIENT_PORT1)
            .buildAndOpen();

        crusher2 = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withLocalAddress(HOSTNAME, CRUSHER_PORT2)
            .withRemoteAddress(HOSTNAME, CLIENT_PORT2)
            .buildAndOpen();
    }

    @After
    public void tearDown() throws Exception {
        if (crusher2 != null) {
            crusher2.close();
        }

        if (crusher1 != null) {
            crusher1.close();
        }

        if (reactor != null) {
            reactor.close();
        }
    }

    @Test
    public void test() throws Exception {
        crusher1.freeze();
        crusher1.unfreeze();

        crusher2.freeze();
        crusher2.unfreeze();

        DatagramBulkClient client1 = new DatagramBulkClient(
            new InetSocketAddress(HOSTNAME, CLIENT_PORT1),
            new InetSocketAddress(HOSTNAME, CRUSHER_PORT2),
            COUNT);
        DatagramBulkClient client2 = new DatagramBulkClient(
            new InetSocketAddress(HOSTNAME, CLIENT_PORT2),
            new InetSocketAddress(HOSTNAME, CRUSHER_PORT1),
            COUNT);

        client1.open();
        client2.open();

        client1.await(10000);
        client2.await(10000);

        Assert.assertArrayEquals(client1.getRcvDigest(), client2.getSndDigest());
        Assert.assertArrayEquals(client2.getRcvDigest(), client1.getSndDigest());

        client1.close();
        client2.close();
    }
}
