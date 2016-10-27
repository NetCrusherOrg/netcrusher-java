package org.netcrusher.datagram.linux;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.datagram.DatagramCrusher;
import org.netcrusher.datagram.DatagramCrusherBuilder;
import org.netcrusher.tcp.linux.TcpCrusherLinuxTest;
import org.netcrusher.test.AbstractLinuxTest;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Future;

public class DatagramCrusherLinuxTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpCrusherLinuxTest.class);

    private static final long BYTES = 2 * 1024 * 1024;

    private NioReactor reactor;

    private DatagramCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress("127.0.0.1", 50100)
            .withConnectAddress("127.0.0.1", 50101)
            .withCreationListener((addr) -> LOGGER.info("Client is created <{}>", addr))
            .withDeletionListener((addr, byteMeters, packetMeters) -> LOGGER.info("Client is deleted <{}>", addr))
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
                    + " | pv -q -L 500k"
                    + " | dd bs=1k"
                    + " | ncat -4 --nodns --send-only --udp 127.0.0.1 50100"
        ));

        ProcessWrapper consumer = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", "ncat -4 --nodns --recv-only --udp --idle-timeout 3 --listen 127.0.0.1 50101"
                    + " | dd bs=1k of=/dev/null"
        ));

        Future<ProcessResult> consumerFuture = consumer.run();
        Future<ProcessResult> producerFuture = producer.run();

        ProcessResult producerResult = producerFuture.get();
        ProcessResult consumerResult = consumerFuture.get();

        LOGGER.info("Producer: \n-----\n{}\n-----\n", producerResult.getOutputText());
        LOGGER.info("Consumer: \n-----\n{}\n-----\n", consumerResult.getOutputText());

        Assert.assertEquals(0, producerResult.getExitCode());
        Assert.assertEquals(2, consumerResult.getExitCode());

        Assert.assertTrue(consumerResult.getOutput().stream().
            anyMatch((s) -> s.startsWith(String.format("%d bytes", BYTES))));

        Assert.assertTrue(consumerResult.getOutput().stream().
            anyMatch((s) -> s.contains("Idle timeout expired")));
    }
}
