package org.netcrusher.datagram.bulk;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

public class DatagramBulkTest {

    private static final int PORT1 = 10082;

    private static final int PORT2 = 10083;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 64 * 1024 * 1024;

    @Test
    public void test() throws Exception {
        DatagramBulkClient client1 = new DatagramBulkClient(
            new InetSocketAddress(HOSTNAME, PORT1),
            new InetSocketAddress(HOSTNAME, PORT2),
            COUNT);
        DatagramBulkClient client2 = new DatagramBulkClient(
            new InetSocketAddress(HOSTNAME, PORT2),
            new InetSocketAddress(HOSTNAME, PORT1),
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
