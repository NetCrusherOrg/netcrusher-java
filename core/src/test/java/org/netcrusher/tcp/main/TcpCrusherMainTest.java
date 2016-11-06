package org.netcrusher.tcp.main;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TcpCrusherMainTest {

    @Test
    public void test() throws Exception {
        String[] arguments = { "127.0.0.1:12345", "google.com:80" };

        TcpCrusherMain.main(arguments);
    }

}
