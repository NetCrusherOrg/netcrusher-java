package org.netcrusher.core.meter;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class RateMeterPeriodTest {

    @Test
    public void test() throws Exception {
        RateMeterPeriod p = new RateMeterPeriod(5000, 1000);

        Assert.assertEquals(5000, p.getCount());
        Assert.assertEquals(1000, p.getElapsedMs());

        Assert.assertEquals(5000, p.getRatePerSec(), 0.1);
        Assert.assertEquals(5000, p.getRatePer(1, TimeUnit.SECONDS), 0.1);

        Assert.assertEquals(0.005, p.getRatePer(1, TimeUnit.MICROSECONDS), 0.0001);
        Assert.assertEquals(5, p.getRatePer(1, TimeUnit.MILLISECONDS), 0.1);
        Assert.assertEquals(300000, p.getRatePer(1, TimeUnit.MINUTES), 0.1);
    }
}