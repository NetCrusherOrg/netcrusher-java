package org.netcrusher.misc;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

/**
 * This test doesn't work on Windows
 * @see <a href="http://stackoverflow.com/q/39656477/827139">Stack overflow</a>
 */
public class ServerSocketChannelCloseTest {

    @Test
    public void test() throws Exception {
        Selector selector = Selector.open();

        for (int i = 0; i < 3; i++) {
            System.out.printf("Trial %d\n", i);
            reopen(selector);
        }
    }

    private void reopen(Selector selector) throws Exception {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        channel.bind(new InetSocketAddress("127.0.0.1", 17777));

        // --- if channel is not registered with selector the following close() method works fine
        SelectionKey selectionKey = channel.register(selector, SelectionKey.OP_ACCEPT);

        // --- trying to cancel the registration in selector - doesn't help
        // selectionKey.cancel();
        // selector.wakeup();

        // --- trying to configure the socket as blocking - doesn't help
        // selectionKey.cancel();
        // channel.configureBlocking(true);

        // --- trying to register the channel in other selector - doesn't help
        // selectionKey.cancel();
        // Selector nullSelector = Selector.open();
        // channel.register(nullSelector, 0);
        // nullSelector.close();

        // --- it helps!!!
        // selectionKey.cancel();
        // selector.selectNow();

        channel.close();

        // PROBLEM: after close() has returned I still see this port is listening
        //
        //     C:\Dev>netstat -nao | grep 17777
        //     TCP    127.0.0.1:17777        0.0.0.0:0              LISTENING       xxxx
        //
        // so on the next bind I get an exception: java.net.BindException: Address already in use: bind

        // --- it helps!!!
        selector.selectNow();

        // --- it helps!!! but I don't want to because there could multiple server sockets on the same selector
        // selector.close();

        // --- trying to shake-up the selector - doesn't help
        // selector.wakeup();

        // --- trying to wait some time - doesn't help
        // Thread.sleep(10000);
    }

}
