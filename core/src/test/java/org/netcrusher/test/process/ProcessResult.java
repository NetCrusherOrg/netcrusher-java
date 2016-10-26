package org.netcrusher.test.process;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

public class ProcessResult implements Serializable {

    private final int exitCode;

    private final List<String> output;

    public ProcessResult(int exitCode, List<String> output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public List<String> getOutput() {
        return output;
    }

    public String getOutputText() {
        StringBuilder sb = new StringBuilder();

        Iterator<String> iterator = output.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(System.lineSeparator());
            }
        }

        return sb.toString();
    }
}
