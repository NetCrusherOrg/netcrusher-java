package org.netcrusher.datagram.bulk;

import org.junit.Assert;
import org.junit.Test;
import org.netcrusher.core.nio.NioUtils;

import java.net.InetSocketAddress;
import java.util.concurrent.CyclicBarrier;

public class DatagramBulkReflectorTest {

    private static final int CLIENT_PORT = 10084;

    private static final int REFLECTOR_PORT = 10085;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 1_000;

    private static final long SEND_WAIT_MS = 20_000;

    private static final long READ_WAIT_MS = 10_000;

    @Test
    public void test() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);

        DatagramBulkClient client = new DatagramBulkClient("CLIENT",
            new InetSocketAddress(HOSTNAME, CLIENT_PORT),
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT),
            COUNT,
            barrier,
            barrier);

        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR",
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT),
            COUNT,
            barrier);

        reflector.open();
        client.open();

        try {
            final byte[] producerDigest = client.awaitProducerResult(SEND_WAIT_MS).getDigest();
            final byte[] consumerDigest = client.awaitConsumerResult(READ_WAIT_MS).getDigest();
            final byte[] reflectorDigest = reflector.awaitReflectorResult(READ_WAIT_MS).getDigest();

            Assert.assertArrayEquals(producerDigest, consumerDigest);
            Assert.assertArrayEquals(producerDigest, reflectorDigest);
        } finally {
            NioUtils.close(client);
            NioUtils.close(reflector);
        }
    }
}
