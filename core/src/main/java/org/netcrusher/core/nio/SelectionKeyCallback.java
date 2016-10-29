package org.netcrusher.core.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionKeyCallback {

    /**
     * Callback for selector's event
     * @param selectionKey Selector that activated the event
     * @throws IOException Throw exception on error
     */
    void execute(SelectionKey selectionKey) throws IOException;

}
