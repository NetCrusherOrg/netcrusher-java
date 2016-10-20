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

public class DeadPortTcpTest {

    private static final int CRUSHER_PORT = 10080;

    private static final int DEAD_PORT = 53654;

    private static final String HOSTNAME = "127.0.0.1";

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(HOSTNAME, CRUSHER_PORT)
            .withConnectAddress(HOSTNAME, DEAD_PORT)
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
        channel.connect(new InetSocketAddress(HOSTNAME, CRUSHER_PORT));

        Assert.assertEquals(0, crusher.getPairs().size());
        Thread.sleep(3000);
        Assert.assertEquals(0, crusher.getPairs().size());

        ByteBuffer bb = ByteBuffer.allocate(100);
        bb.put((byte) 0x01);
        bb.flip();

        try {
            channel.write(bb);
            Assert.fail("Exception is expected");
        } catch (IOException e) {
            //
        }
    }
}
