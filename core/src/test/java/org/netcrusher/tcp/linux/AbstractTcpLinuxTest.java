package org.netcrusher.tcp.linux;

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

public abstract class AbstractTcpLinuxTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTcpLinuxTest.class);

    protected static final int DEFAULT_BYTES = 256 * 1024 * 1024;

    protected static final int DEFAULT_THROUGHPUT_KBPERSEC = 100_000;

    protected static final int FULL_THROUGHPUT = 0;

    /* IP4 */

    protected static final String SOCAT4_PROCESSOR =
        SOCAT4 + " - tcp4:127.0.0.1:50100,ignoreeof";

    protected static final String SOCAT4_REFLECTOR_DIRECT =
        SOCAT4 + " -b 16384 PIPE tcp4-listen:50100,bind=127.0.0.1,reuseaddr";

    protected static final String SOCAT4_REFLECTOR_PROXIED =
        SOCAT4 + " -b 16384 PIPE tcp4-listen:50101,bind=127.0.0.1,reuseaddr";

    protected static final String SOCAT4_PRODUCER =
        SOCAT4 + " - tcp4:127.0.0.1:50100";

    protected static final String SOCAT4_CONSUMER_DIRECT =
        SOCAT4 + " - tcp4-listen:50100,bind=127.0.0.1,reuseaddr";

    protected static final String SOCAT4_CONSUMER_PROXIED =
        SOCAT4 + " - tcp4-listen:50101,bind=127.0.0.1,reuseaddr";

    /* IP6 */

    protected static final String SOCAT6_PROCESSOR =
        SOCAT6 + " - tcp6:[::1]:50100,ignoreeof";

    protected static final String SOCAT6_REFLECTOR_DIRECT =
        SOCAT6 + " -b 16384 PIPE tcp6-listen:50100,bind=[::1],reuseaddr";

    protected static final String SOCAT6_REFLECTOR_PROXIED =
        SOCAT6 + " -b 16384 PIPE tcp6-listen:50101,bind=[::1],reuseaddr";

    protected static final String SOCAT6_PRODUCER =
        SOCAT6 + " - tcp6:[::1]:50100";

    protected static final String SOCAT6_CONSUMER_DIRECT =
        SOCAT6 + " - tcp6-listen:50100,bind=[::1],reuseaddr";

    protected static final String SOCAT6_CONSUMER_PROXIED =
        SOCAT6 + " - tcp6-listen:50101,bind=[::1],reuseaddr";

    protected ProcessResult loop(String processorCmd, String reflectorCmd, int bytes, int throughputKbSec)
        throws Exception
    {
        ProcessWrapper processor = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "openssl rand " + bytes
                + " | tee >(openssl md5 >&2)"
                + ((throughputKbSec > 0) ? " | pv -q -L " + throughputKbSec + "K" : "")
                + " | dd bs=1M"
                + " | " + processorCmd
                + " | dd bs=1M"
                + " | openssl md5 >&2"
        ));

        ProcessWrapper reflector = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", reflectorCmd
        ));

        Future<ProcessResult> reflectorFuture = reflector.run();
        Future<ProcessResult> processorFuture = processor.run();

        ProcessResult processorResult = processorFuture.get();
        ProcessResult reflectorResult = reflectorFuture.get();

        output(LOGGER, "Processor", processorResult.getOutputText());
        output(LOGGER, "Reflector", reflectorResult.getOutputText());

        Assert.assertEquals(0, processorResult.getExitCode());
        Assert.assertEquals(0, reflectorResult.getExitCode());

        Assert.assertEquals(2, processorResult.getOutput().stream()
            .filter((s) -> s.startsWith(String.format("%d bytes", bytes)))
            .count()
        );

        List<String> hashes = extractMd5(processorResult.getOutput())
            .collect(Collectors.toList());

        Assert.assertEquals(2, hashes.size());
        Assert.assertEquals(hashes.get(0), hashes.get(1));

        return processorResult;
    }

    protected ProcessResult direct(String producerCmd, String consumerCmd, int bytes, int throughputKbSec)
        throws Exception
    {
        ProcessWrapper producer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "openssl rand " + bytes
                + " | tee >(openssl md5 >&2)"
                + ((throughputKbSec > 0) ? " | pv -q -L " + throughputKbSec + "K" : "")
                + " | dd bs=1M"
                + " | " + producerCmd
        ));

        ProcessWrapper consumer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c",  consumerCmd
                + " | dd bs=1M"
                + " | openssl md5 >&2"
        ));

        Future<ProcessResult> reflectorFuture = consumer.run();
        Future<ProcessResult> processorFuture = producer.run();

        ProcessResult producerResult = processorFuture.get();
        ProcessResult consumerResult = reflectorFuture.get();

        output(LOGGER, "Producer", producerResult.getOutputText());
        output(LOGGER, "Consumer", consumerResult.getOutputText());

        Assert.assertEquals(0, producerResult.getExitCode());
        Assert.assertEquals(0, consumerResult.getExitCode());

        Assert.assertEquals(1, producerResult.getOutput().stream()
            .filter((s) -> s.startsWith(String.format("%d bytes", bytes)))
            .count()
        );
        Assert.assertEquals(1, consumerResult.getOutput().stream()
            .filter((s) -> s.startsWith(String.format("%d bytes", bytes)))
            .count()
        );

        String producerHash = extractMd5(producerResult.getOutput())
            .findFirst()
            .orElse("no-producer-hash");
        String consumerHash = extractMd5(consumerResult.getOutput())
            .findFirst()
            .orElse("no-consumer-hash");

        Assert.assertEquals(producerHash, consumerHash);

        return consumerResult;
    }
}
