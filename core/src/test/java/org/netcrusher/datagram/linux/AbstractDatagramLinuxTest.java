package org.netcrusher.datagram.linux;

import org.junit.Assert;
import org.netcrusher.test.AbstractLinuxTest;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public abstract class AbstractDatagramLinuxTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatagramLinuxTest.class);

    protected static final int DEFAULT_BYTES = 8 * 1024 * 1024;

    protected void session(int bytes, int sndPort, int rcvPort) throws Exception {
        ProcessWrapper processor = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "openssl rand " + bytes
                + " | tee >(openssl md5 >&2)"
                + " | pv -q -L 1M"
                + " | dd bs=1024"
                + " | socat -T3 -4 - udp4-sendto:127.0.0.1:" + sndPort + ",ignoreeof"
                + " | dd bs=1024"
                + " | openssl md5 >&2"
        ));

        ProcessWrapper reflector = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "socat -T3 -4 -b 16384 PIPE udp4-listen:" + rcvPort + ",bind=127.0.0.1,reuseaddr"
        ));

        Future<ProcessResult> reflectorFuture = reflector.run();
        Future<ProcessResult> processorFuture = processor.run();

        ProcessResult processorResult = processorFuture.get();
        ProcessResult reflectorResult = reflectorFuture.get();

        LOGGER.info("Processor: \n-----\n{}\n-----\n", processorResult.getOutputText());
        LOGGER.info("Reflector: \n-----\n{}\n-----\n", reflectorResult.getOutputText());

        Assert.assertEquals(0, processorResult.getExitCode());
        Assert.assertEquals(0, reflectorResult.getExitCode());

        Assert.assertEquals(2, processorResult.getOutput().stream()
            .filter((s) -> s.startsWith(String.format("%d bytes", bytes)))
            .count()
        );

        List<String> hashes = processorResult.getOutput().stream()
            .filter((s) -> MD5_PATTERN.matcher(s).find())
            .collect(Collectors.toList());

        Assert.assertEquals(2, hashes.size());
        Assert.assertEquals(hashes.get(0), hashes.get(1));
    }
}
