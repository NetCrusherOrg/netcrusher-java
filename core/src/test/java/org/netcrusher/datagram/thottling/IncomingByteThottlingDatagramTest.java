package org.netcrusher.datagram.thottling;

import org.junit.Assert;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.core.throttle.rate.ByteRateThrottler;
import org.netcrusher.datagram.DatagramCrusher;
import org.netcrusher.datagram.DatagramCrusherBuilder;
import org.netcrusher.datagram.bulk.DatagramBulkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class IncomingByteThottlingDatagramTest extends AbstractRateThottlingDatagramTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingByteThottlingDatagramTest.class);

    private static final int BYTES_PER_SEC = 20_000;

    @Override
    protected DatagramCrusher createCrusher(NioReactor reactor, String host, int bindPort, int connectPort) {
        return DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(host, bindPort)
            .withConnectAddress(host, connectPort)
            .withIncomingGlobalThrottler(new ByteRateThrottler(BYTES_PER_SEC, 1, TimeUnit.SECONDS))
            .withCreationListener((addr) -> LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters, packetMeters) -> LOGGER.info("Client is deleted <{}>", addr))
            .buildAndOpen();
    }

    @Override
    protected void verify(
        DatagramBulkResult producerResult,
        DatagramBulkResult consumerResult,
        DatagramBulkResult reflectorResult,
        double precisionAllowed)
    {
        double consumerRate = 1000.0 * consumerResult.getBytes() / consumerResult.getElapsedMs();
        LOGGER.info("Consumer rate is {} bytes/sec", consumerRate);

        Assert.assertEquals(BYTES_PER_SEC, consumerRate, BYTES_PER_SEC * precisionAllowed);
        Assert.assertArrayEquals(producerResult.getDigest(), reflectorResult.getDigest());
    }

}
