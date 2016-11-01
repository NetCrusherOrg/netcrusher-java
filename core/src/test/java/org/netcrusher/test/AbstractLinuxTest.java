package org.netcrusher.test;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.slf4j.Logger;

import java.util.regex.Pattern;

public abstract class AbstractLinuxTest {

    protected static final Pattern MD5_PATTERN = Pattern.compile("[0-9abcdef]{32}");

    protected static final int SOCAT_TIMEOUT_SEC = 3;

    protected static final String SOCAT4 = String.format("socat -d -T%d -4", SOCAT_TIMEOUT_SEC);

    protected static final String SOCAT6 = String.format("socat -d -T%d -6", SOCAT_TIMEOUT_SEC);

    protected static final String ADDR_LOOPBACK4 = "127.0.0.1";

    protected static final String ADDR_LOOPBACK6 = "::1";

    protected static final int PORT_DIRECT = 50100;

    protected static final int PORT_PROXY = 50101;

    @BeforeClass
    public static void checkLinux() throws Exception {
        String os = System.getProperty("os.name");
        Assume.assumeTrue("This test runs only on Linux", "linux".equalsIgnoreCase(os));

        Assume.assumeFalse("Linux tests are disabled", Boolean.getBoolean("disable.linux.tests"));
    }

    protected static void output(Logger logger, String name, String lines) {
        logger.info(
                "{}:\n" +
                "--------------------------------------------------------\n" +
                "{}\n" +
                "--------------------------------------------------------",
            name, lines);
    }

}
