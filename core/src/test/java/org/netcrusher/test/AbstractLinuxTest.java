package org.netcrusher.test;

import org.junit.Assume;
import org.junit.Before;

import java.util.regex.Pattern;

public abstract class AbstractLinuxTest {

    protected static final Pattern MD5_PATTERN = Pattern.compile("[0-9abcdef]{32}");

    @Before
    public void checkLinux() throws Exception {
        String os = System.getProperty("os.name");
        Assume.assumeTrue("linux".equalsIgnoreCase(os));
    }

}
