package org.netcrusher.test;

import org.junit.Assert;
import org.junit.Test;
import org.netcrusher.test.process.ProcessResult;
import org.netcrusher.test.process.ProcessWrapper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

public class CheckLinuxTest extends AbstractLinuxTest {

    @Test
    public void check() throws Exception {
        Assert.assertTrue("<socat> is not found", ensureCommand(Arrays.asList("socat", "-V")));
        Assert.assertTrue("<openssl> is not found", ensureCommand(Arrays.asList("openssl", "version")));
        Assert.assertTrue("<pv> is not found", ensureCommand(Arrays.asList("pv", "-V")));
        Assert.assertTrue("<tee> is not found", ensureCommand(Arrays.asList("tee", "--version")));
        Assert.assertTrue("<dd> is not found", ensureCommand(Arrays.asList("dd", "--version")));
        Assert.assertTrue("<bash> is not found", ensureCommand(Arrays.asList("bash", "--version")));
    }

    private static boolean ensureCommand(List<String> commands) throws Exception {
        ProcessWrapper wrapper = new ProcessWrapper(commands);

        Future<ProcessResult> future = wrapper.run();

        return future.get().getExitCode() == 0;
    }

}
