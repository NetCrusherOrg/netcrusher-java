package org.netcrusher.datagram.linux;

import org.junit.Assert;
import org.netcrusher.test.AbstractLinuxTest;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Future;

public abstract class AbstractDatagramLinuxTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatagramLinuxTest.class);

    protected static final int DEFAULT_BYTES = 2 * 1024 * 1024;

    protected void loop(int bytes, int sndPort, int rcvPort) throws Exception {
        ProcessWrapper producer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "openssl rand " + bytes
                + " | tee >(openssl md5 >&2)"
                + " | pv -q -L 1M"
                + " | dd bs=1024"
                + " | socat -4 - udp4-sendto:127.0.0.1:" + sndPort
        ));

        ProcessWrapper consumer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "socat -T3 -4 - udp4-listen:" + rcvPort + ",bind=127.0.0.1,reuseaddr"
                + " | tee >(openssl md5 >&2)"
                + " | dd bs=1k of=/dev/null"
        ));

        Future<ProcessResult> consumerFuture = consumer.run();
        Future<ProcessResult> producerFuture = producer.run();

        ProcessResult producerResult = producerFuture.get();
        ProcessResult consumerResult = consumerFuture.get();

        LOGGER.info("Producer: \n-----\n{}\n-----\n", producerResult.getOutputText());
        LOGGER.info("Consumer: \n-----\n{}\n-----\n", consumerResult.getOutputText());

        Assert.assertEquals(0, producerResult.getExitCode());
        Assert.assertEquals(0, consumerResult.getExitCode());

        Assert.assertTrue(consumerResult.getOutput().stream()
            .anyMatch((s) -> s.startsWith(String.format("%d bytes", bytes))));

        String producerMd5 = producerResult.getOutput().stream()
            .filter((s) -> MD5_PATTERN.matcher(s).find())
            .findFirst()
            .orElse("no-producer-md5");
        String consumerMd5 = consumerResult.getOutput().stream()
            .filter((s) -> MD5_PATTERN.matcher(s).find())
            .findFirst()
            .orElse("no-consumer-md5");
        Assert.assertEquals(producerMd5, consumerMd5);
    }
}
