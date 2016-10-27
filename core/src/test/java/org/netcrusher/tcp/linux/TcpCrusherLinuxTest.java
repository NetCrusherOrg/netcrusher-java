package org.netcrusher.tcp.linux;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.netcrusher.test.AbstractLinuxTest;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Future;

public class TcpCrusherLinuxTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpCrusherLinuxTest.class);

    private static final long BYTES = 256 * 1024 * 1024;

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress("127.0.0.1", 50100)
            .withConnectAddress("127.0.0.1", 50101)
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
        ProcessWrapper producer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "openssl rand " + BYTES
                    + " | dd bs=1M"
                    + " | ncat -4 --nodns --send-only 127.0.0.1 50100"));

        ProcessWrapper consumer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "ncat -4 --nodns --recv-only --listen 127.0.0.1 50101"
                    + " | dd bs=1M of=/dev/null"));

        Future<ProcessResult> consumerFuture = consumer.run();
        Future<ProcessResult> producerFuture = producer.run();

        ProcessResult producerResult = producerFuture.get();
        ProcessResult consumerResult = consumerFuture.get();

        LOGGER.info("Producer: \n-----\n{}\n-----\n", producerResult.getOutputText());
        LOGGER.info("Consumer: \n-----\n{}\n-----\n", consumerResult.getOutputText());

        Assert.assertEquals(0, producerResult.getExitCode());
        Assert.assertEquals(1, consumerResult.getExitCode());

        Assert.assertTrue(consumerResult.getOutput().stream().
            anyMatch((s) -> s.startsWith(String.format("%d bytes", BYTES))));
    }

}
