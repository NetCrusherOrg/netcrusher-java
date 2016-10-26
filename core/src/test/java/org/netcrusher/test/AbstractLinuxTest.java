package org.netcrusher.test;

import org.junit.Assume;
import org.junit.Before;

public abstract class AbstractLinuxTest {

    @Before
    public void checkLinux() throws Exception {
        String os = System.getProperty("os.name");
        Assume.assumeTrue("linux".equalsIgnoreCase(os));
    }

}
