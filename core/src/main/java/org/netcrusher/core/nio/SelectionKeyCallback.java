package org.netcrusher.core.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionKeyCallback {

    void execute(SelectionKey selectionKey) throws IOException;

}
