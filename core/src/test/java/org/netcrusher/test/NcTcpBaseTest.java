package org.netcrusher.test;

import org.junit.Assert;
import org.junit.Test;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Future;

public class NcTcpBaseTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NcTcpBaseTest.class);

    private static final long BYTES = 128 * 1024 * 1024;

    @Test
    public void test() throws Exception {
        ProcessWrapper producer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "openssl rand " + BYTES + " | dd bs=1M | ncat -4 --nodns --send-only 127.0.0.1 50101"));

        ProcessWrapper consumer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "ncat -4 --nodns --recv-only --listen 127.0.0.1 50101 | dd bs=1M of=/dev/null"));

        Future<ProcessResult> consumerFuture = consumer.run();
        Future<ProcessResult> producerFuture = producer.run();

        ProcessResult producerResult = producerFuture.get();
        ProcessResult consumerResult = consumerFuture.get();

        LOGGER.info("Producer: \n-----\n{}\n-----\n", producerResult.getOutputText());
        LOGGER.info("Consumer: \n-----\n{}\n-----\n", consumerResult.getOutputText());

        Assert.assertEquals(0, producerResult.getExitCode());
        Assert.assertEquals(0, consumerResult.getExitCode());

        Assert.assertTrue(consumerResult.getOutput().stream().
            anyMatch((s) -> s.startsWith(String.format("%d bytes", BYTES))));
    }
}
