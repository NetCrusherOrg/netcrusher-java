package org.netcrusher.datagram;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class UnknownHostDatagramTest {

    private static final int PORT_CRUSHER = 10283;

    private static final int PORT_CONNECT = 10284;

    private static final String HOSTNAME_BIND = "127.0.0.1";

    private static final String HOSTNAME_CONNECT = "192.168.251.252";

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME_BIND, PORT_CRUSHER)
            .withConnectAddress(HOSTNAME_CONNECT, PORT_CONNECT)
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
    }

    @Test
    public void test() throws Exception {
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(HOSTNAME_BIND, PORT_CRUSHER));

        try {
            ByteBuffer bb = ByteBuffer.allocate(1024);
            bb.limit(800);
            bb.position(0);

            channel.write(bb);

            Thread.sleep(1002);
        } finally {
            channel.close();
        }
    }
}
