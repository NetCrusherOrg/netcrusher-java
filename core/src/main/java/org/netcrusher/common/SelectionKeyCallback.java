package org.netcrusher.common;

import java.io.IOException;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionKeyCallback {

    void execute(SelectionKey selectionKey) throws IOException;

}
