package org.netcrusher.datagram.main;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class DatagramCrusherMainTest {

    @Test
    public void test() throws Exception {
        String[] arguments = { "127.0.0.1:12345", "google.com:80" };

        DatagramCrusherMain.main(arguments);
    }
}
