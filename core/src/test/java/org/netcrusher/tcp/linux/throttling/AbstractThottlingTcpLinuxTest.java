package org.netcrusher.tcp.linux.throttling;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.rate.ByteRateThrottler;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.netcrusher.tcp.linux.AbstractTcpLinuxTest;
import org.netcrusher.test.process.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractThottlingTcpLinuxTest extends AbstractTcpLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractThottlingTcpLinuxTest.class);

    private static final double PRECISION = 0.05;

    private static final Pattern DURATION = Pattern.compile(", (\\d+(\\.\\d+)?) s,", Pattern.MULTILINE);

    private NioReactor reactor;

    private TcpCrusher crusher;

    private final int bytePerSec;

    private final int durationSec;

    public AbstractThottlingTcpLinuxTest(int bytePerSec, int durationSec) {
        this.bytePerSec = bytePerSec;
        this.durationSec = durationSec;
    }

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor(10);

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(ADDR_LOOPBACK4, PORT_DIRECT)
            .withConnectAddress(ADDR_LOOPBACK4, PORT_PROXY)
            .withBufferSize(bytePerSec / 100)
            .withBufferCount(128)
            .withRcvBufferSize(bytePerSec / 2)
            .withSndBufferSize(bytePerSec / 2)
            .withOutgoingThrottlerFactory((addr) ->
                new ByteRateThrottler(bytePerSec / 50, 1000 / 50, TimeUnit.MILLISECONDS))
            .withCreationListener((addr) -> LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters) -> LOGGER.info("Client is deleted <{}>", addr))
            .buildAndOpen();
    }

    @After
    public void tearDown() throws Exception {
        if (crusher != null) {
            crusher.close();
            Assert.assertFalse(crusher.isOpen());
        }

        if (reactor != null) {
            reactor.close();
            Assert.assertFalse(reactor.isOpen());
        }
    }

    @Test
    public void test() throws Exception {
        ProcessResult result = loop(SOCAT4_PROCESSOR, SOCAT4_REFLECTOR_PROXIED, durationSec * bytePerSec, FULL_THROUGHPUT);

        String consumerDuration = result.getOutput().stream()
            .map((s) -> {
                Matcher matcher = DURATION.matcher(s);
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .skip(1)
            .findFirst()
            .orElse("none");

        double duration = Double.parseDouble(consumerDuration);
        LOGGER.info("Duration: {} sec", duration);

        Assert.assertEquals(this.durationSec, duration, this.bytePerSec * PRECISION);
    }

}
