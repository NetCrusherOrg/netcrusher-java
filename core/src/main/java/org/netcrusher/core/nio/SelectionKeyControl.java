package org.netcrusher.core.nio;

import java.nio.channels.SelectionKey;

public class SelectionKeyControl {

    private final SelectionKey selectionKey;

    public SelectionKeyControl(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public boolean isValid() {
         return selectionKey.isValid();
    }

    public void enableReads() {
        NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_READ);
    }

    public void enableWrites() {
        NioUtils.setupInterestOps(selectionKey, SelectionKey.OP_WRITE);
    }

    public void disableReads() {
        NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_READ);
    }

    public void disableWrites() {
        NioUtils.clearInterestOps(selectionKey, SelectionKey.OP_WRITE);
    }

    public void setNone() {
        selectionKey.interestOps(0);
    }

    public void setAll() {
        selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    public void setReadsOnly() {
        selectionKey.interestOps(SelectionKey.OP_READ);
    }

    public void setWritesOnly() {
        selectionKey.interestOps(SelectionKey.OP_WRITE);
    }
}
