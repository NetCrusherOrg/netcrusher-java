package org.netcrusher.core.throttle.rate;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.chronometer.MockChronometer;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ByteRateThrottlerTest {

    private static final long RATE_PER_SEC = 1000;

    private ByteBuffer stubBuffer;

    private MockChronometer mockChronometer;

    private ByteRateThrottler throttler;

    @Before
    public void setUp() throws Exception {
        this.stubBuffer = ByteBuffer.allocate(10000);

        this.mockChronometer = new MockChronometer();

        this.throttler = new ByteRateThrottler(RATE_PER_SEC, 1, TimeUnit.SECONDS,
            AbstractRateThrottler.AUTO_FACTOR, mockChronometer);
    }

    @Test
    public void testBulk() throws Exception {
        long totalSent = 0;
        long totalElapsedNs = 0;

        Random random = new Random(1);

        for (int i = 0; i < 10_000; i++) {
            int bufferSize = random.nextInt(100);
            stubBuffer.limit(bufferSize);

            long elapsedNs = random.nextInt(100_000);
            mockChronometer.add(elapsedNs, TimeUnit.NANOSECONDS);

            long delayNs = throttler.calculateDelayNs(stubBuffer);
            mockChronometer.add(delayNs, TimeUnit.NANOSECONDS);

            totalSent += bufferSize;
            totalElapsedNs += elapsedNs;
            totalElapsedNs += delayNs;
        }

        double ratePerSec = 1.0 * TimeUnit.SECONDS.toNanos(1) * totalSent / totalElapsedNs;
        Assert.assertEquals(RATE_PER_SEC, ratePerSec, 0.01 * RATE_PER_SEC);
    }

    @Test
    public void testSmallRate() throws Exception {
        // 1 byte per 100 seconds
        ByteRateThrottler lazyThrottler = new ByteRateThrottler(1, 100, TimeUnit.SECONDS,
            AbstractRateThrottler.AUTO_FACTOR, mockChronometer);

        mockChronometer.add(1, TimeUnit.SECONDS);

        stubBuffer.limit(1);

        long delayNs = lazyThrottler.calculateDelayNs(stubBuffer);
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(99), delayNs);
    }
}
