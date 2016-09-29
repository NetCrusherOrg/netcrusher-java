package org.netcrusher.datagram.bulk;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

public class DatagramBulkReflectorTest {

    private static final int CLIENT_PORT = 10084;

    private static final int REFLECTOR_PORT = 10085;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 2 * 1_000_000;

    @Test
    public void test() throws Exception {
        DatagramBulkClient client = new DatagramBulkClient("CLIENT",
            new InetSocketAddress(HOSTNAME, CLIENT_PORT),
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT),
            COUNT);
        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR",
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT));

        reflector.open();
        client.open();

        client.await(10000);

        client.close();
        reflector.close();

        Assert.assertNotNull(client.getRcvDigest());

        Assert.assertArrayEquals(client.getRcvDigest(), client.getSndDigest());
    }
}
