package org.netcrusher.tcp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.NetCrusherException;
import org.netcrusher.core.reactor.NioReactor;

import java.net.InetSocketAddress;

public class CycleTcpTest {

    private static final InetSocketAddress CRUSHER_ADDRESS = new InetSocketAddress("127.0.0.1", 10284);

    private static final InetSocketAddress REFLECTOR_ADDRESS = new InetSocketAddress("127.0.0.1", 10285);

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(CRUSHER_ADDRESS)
            .withConnectAddress(REFLECTOR_ADDRESS)
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

    @Test(expected = NetCrusherException.class)
    public void doubleOpen() throws Exception {
        crusher.open();
    }

    @Test
    public void doubleClose() throws Exception {
        crusher.close();
        crusher.close();
    }

    @Test(expected = NetCrusherException.class)
    public void doubleFreeze() throws Exception {
        crusher.freeze();
        crusher.freeze();
    }

    @Test(expected = NetCrusherException.class)
    public void doubleUnreeze() throws Exception {
        crusher.freeze();
        crusher.unfreeze();
        crusher.unfreeze();
    }

    @Test(expected = NetCrusherException.class)
    public void unreezeWithoutFreeze() throws Exception {
        crusher.unfreeze();
    }

    @Test
    public void reopen() throws Exception {
        crusher.reopen();
    }

    @Test(expected = NetCrusherException.class)
    public void reopenAfterClose() throws Exception {
        crusher.close();
        crusher.reopen();
    }
}
