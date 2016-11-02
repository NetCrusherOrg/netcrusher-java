package org.netcrusher.datagram.bulk;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CyclicBarrier;

public class DatagramBulkClientTest {

    private static final int CLIENT1_PORT = 10082;

    private static final int CLIENT2_PORT = 10083;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 2 * 1_000_000;

    private static final long SEND_WAIT_MS = 20_000;

    private static final long READ_WAIT_MS = 10_000;

    @Test
    public void test() throws Exception {
        CyclicBarrier barrier1 = new CyclicBarrier(2);
        CyclicBarrier barrier2 = new CyclicBarrier(2);

        DatagramBulkClient client1 = new DatagramBulkClient("CLIENT1",
            new InetSocketAddress(HOSTNAME, CLIENT1_PORT),
            new InetSocketAddress(HOSTNAME, CLIENT2_PORT),
            COUNT,
            barrier1,
            barrier2);

        DatagramBulkClient client2 = new DatagramBulkClient("CLIENT2",
            new InetSocketAddress(HOSTNAME, CLIENT2_PORT),
            new InetSocketAddress(HOSTNAME, CLIENT1_PORT),
            COUNT,
            barrier2,
            barrier1);

        client1.open();
        client2.open();


        final byte[] producer1Digest = client1.awaitProducerDigest(SEND_WAIT_MS);
        final byte[] producer2Digest = client2.awaitProducerDigest(SEND_WAIT_MS);

        final byte[] consumer1Digest = client1.awaitConsumerDigest(READ_WAIT_MS);
        final byte[] consumer2Digest = client2.awaitConsumerDigest(READ_WAIT_MS);

        client1.close();
        client2.close();

        Assert.assertNotNull(producer1Digest);
        Assert.assertNotNull(producer2Digest);

        Assert.assertArrayEquals(producer1Digest, consumer2Digest);
        Assert.assertArrayEquals(producer2Digest, consumer1Digest);
    }
}
