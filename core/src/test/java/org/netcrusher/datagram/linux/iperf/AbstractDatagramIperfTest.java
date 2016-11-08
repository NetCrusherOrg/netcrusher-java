package org.netcrusher.datagram.linux.iperf;

import org.junit.Assert;
import org.netcrusher.test.AbstractLinuxTest;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Future;

public class AbstractDatagramIperfTest extends AbstractLinuxTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatagramIperfTest.class);

    protected static final int PORT_IPERF = 50100;

    protected static final int PORT_CRUSHER = 50101;

    protected static final String IPERF_SERVER =
        "iperf3 --server --format K --port 50100";

    protected static final String IPERF4_CLIENT_DIRECT =
        "iperf3 --udp --bandwidth 1M --client 127.0.0.1 --port 50100 --time 10 --format m --get-server-output --version4";

    protected static final String IPERF4_CLIENT_PROXIED =
        "iperf3 --udp --bandwidth 1M --client 127.0.0.1 --port 50101 --time 10 --format m --get-server-output --version4";

    protected ProcessResult loop(String serverCmd, String clientCmd) throws Exception {
        ProcessWrapper server = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", serverCmd
        ));

        ProcessWrapper client = new ProcessWrapper(Arrays.asList(
            "bash",
            "-o", "pipefail",
            "-c", clientCmd
        ));

        Future<ProcessResult> serverFuture = server.run();
        Thread.sleep(2000);
        Future<ProcessResult> clientFuture = client.run();

        ProcessResult clientResult = clientFuture.get();
        serverFuture.cancel(true);

        output(LOGGER, "Client", clientResult.getOutputText());

        Assert.assertEquals(0, clientResult.getExitCode());

        return clientResult;
    }

}
