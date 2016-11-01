package org.netcrusher.tcp;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class UnknownPortTcpTest {

    private static final int PORT_CRUSHER = 10080;

    private static final int PORT_UNKNOWN = 53654;

    private static final String HOSTNAME = "127.0.0.1";

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, PORT_CRUSHER)
            .withConnectAddress(HOSTNAME, PORT_UNKNOWN)
            .withConnectionTimeoutMs(500)
            .withBacklog(100)
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
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(HOSTNAME, PORT_CRUSHER));

        try {
            Assert.assertEquals(0, crusher.getClientAddresses().size());
            Thread.sleep(3001);
            Assert.assertEquals(0, crusher.getClientAddresses().size());

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.put((byte) 0x01);
            bb.flip();

            try {
                channel.write(bb);
                Assert.fail("Exception is expected");
            } catch (IOException e) {
                //
            }
        } finally {
            channel.close();
        }
    }
}
