package org.netcrusher.core.meter;

import org.junit.Assert;
import org.junit.Test;
import org.netcrusher.core.chronometer.MockChronometer;

import java.util.concurrent.TimeUnit;

public class RateMeterImplTest {

    @Test
    public void test() throws Exception {
        MockChronometer mockChronometer = new MockChronometer();

        RateMeterImpl rateMeter = new RateMeterImpl(mockChronometer);

        rateMeter.update(100);

        mockChronometer.add(1, TimeUnit.SECONDS);

        Assert.assertEquals(100, rateMeter.getTotalCount());
        Assert.assertEquals(1000, rateMeter.getTotalElapsedMs());

        RateMeterPeriod total = rateMeter.getTotal();
        Assert.assertEquals(100, total.getCount());
        Assert.assertEquals(1000, total.getElapsedMs());
        Assert.assertEquals(100, total.getRatePerSec(), 0.1);
        Assert.assertEquals(0.1, total.getRatePer(1, TimeUnit.MILLISECONDS), 0.01);

        RateMeterPeriod period = rateMeter.getPeriod(true);
        Assert.assertEquals(100, period.getCount());
        Assert.assertEquals(1000, period.getElapsedMs());
        Assert.assertEquals(100, period.getRatePerSec(), 0.1);
        Assert.assertEquals(0.1, period.getRatePer(1, TimeUnit.MILLISECONDS), 0.01);

        period = rateMeter.getPeriod(true);
        Assert.assertEquals(0, period.getCount());
        Assert.assertEquals(0, period.getElapsedMs());
        Assert.assertEquals(Double.NaN, period.getRatePerSec(), 0.1);
    }
}
