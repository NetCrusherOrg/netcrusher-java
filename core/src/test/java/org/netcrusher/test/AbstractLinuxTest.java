package org.netcrusher.test;

import org.junit.Assume;
import org.junit.Before;
import org.slf4j.Logger;

import java.util.regex.Pattern;

public abstract class AbstractLinuxTest {

    protected static final Pattern MD5_PATTERN = Pattern.compile("[0-9abcdef]{32}");

    protected static final String SOCAT4 = "socat -d -T5 -4";

    protected static final String SOCAT6 = "socat -d -T5 -6";

    @Before
    public void checkLinux() throws Exception {
        String os = System.getProperty("os.name");
        Assume.assumeTrue("linux".equalsIgnoreCase(os));
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
