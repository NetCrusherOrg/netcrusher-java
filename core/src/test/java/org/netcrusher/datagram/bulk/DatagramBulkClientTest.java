package org.netcrusher.datagram.bulk;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

public class DatagramBulkClientTest {

    private static final int CLIENT1_PORT = 10082;

    private static final int CLIENT2_PORT = 10083;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 2 * 1_000_000;

    @Test
    public void test() throws Exception {
        DatagramBulkClient client1 = new DatagramBulkClient("CLIENT1",
            new InetSocketAddress(HOSTNAME, CLIENT1_PORT),
            new InetSocketAddress(HOSTNAME, CLIENT2_PORT),
            COUNT);
        DatagramBulkClient client2 = new DatagramBulkClient("CLIENT2",
            new InetSocketAddress(HOSTNAME, CLIENT2_PORT),
            new InetSocketAddress(HOSTNAME, CLIENT1_PORT),
            COUNT);

        client1.open();
        client2.open();

        client1.await(10000);
        client2.await(10000);

        client1.close();
        client2.close();

        Assert.assertNotNull(client1.getRcvDigest());
        Assert.assertNotNull(client2.getRcvDigest());

        Assert.assertArrayEquals(client1.getRcvDigest(), client2.getSndDigest());
        Assert.assertArrayEquals(client2.getRcvDigest(), client1.getSndDigest());
    }
}
