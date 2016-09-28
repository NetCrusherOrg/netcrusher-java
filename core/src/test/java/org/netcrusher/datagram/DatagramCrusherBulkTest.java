package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.common.NioReactor;
import org.netcrusher.datagram.bulk.DatagramBulkClient;
import org.netcrusher.datagram.bulk.DatagramBulkReflector;
import org.netcrusher.filter.CopyFilter;
import org.netcrusher.filter.InverseFilter1;
import org.netcrusher.filter.InverseFilter2;
import org.netcrusher.filter.NopeFilter;

import java.net.InetSocketAddress;

public class DatagramCrusherBulkTest {

    private static final int CLIENT_PORT = 10182;

    private static final int CRUSHER_PORT = 10183;

    private static final int REFLECTOR_PORT = 10184;

    private static final String HOSTNAME = "127.0.0.1";

    private static final long COUNT = 32 * 1_000_000;

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withLocalAddress(HOSTNAME, CRUSHER_PORT)
            .withRemoteAddress(HOSTNAME, REFLECTOR_PORT)
            .buildAndOpen();

        crusher.getFilters().getOutgoing()
            .append(InverseFilter2.FACTORY)
            .append(CopyFilter.FACTORY)
            .append(NopeFilter.FACTORY);

        crusher.getFilters().getIncoming()
            .append(NopeFilter.FACTORY)
            .append(InverseFilter1.FACTORY)
            .append(CopyFilter.FACTORY);
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
    public void test() throws Exception {
        crusher.freeze();
        crusher.unfreeze();

        DatagramBulkClient client = new DatagramBulkClient("CLIENT",
            new InetSocketAddress(HOSTNAME, CLIENT_PORT),
            new InetSocketAddress(HOSTNAME, CRUSHER_PORT),
            COUNT);
        DatagramBulkReflector reflector = new DatagramBulkReflector("REFLECTOR",
            new InetSocketAddress(HOSTNAME, REFLECTOR_PORT),
            COUNT);

        reflector.open();
        client.open();

        client.await(60000);

        client.close();
        reflector.close();

        Assert.assertNotNull(client.getRcvDigest());

        Assert.assertArrayEquals(client.getRcvDigest(), client.getSndDigest());
    }
}
