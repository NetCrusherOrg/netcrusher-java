package org.netcrusher.core.reactor;

import java.io.IOException;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionKeyCallback {

    void execute(SelectionKey selectionKey) throws IOException;

}
