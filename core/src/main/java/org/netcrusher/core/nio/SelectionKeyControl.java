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

    public void enable(int operations) {
        NioUtils.setupInterestOps(selectionKey, operations);
    }

    public void enableReads() {
        enable(SelectionKey.OP_READ);
    }

    public void enableWrites() {
        enable(SelectionKey.OP_WRITE);
    }

    public void disable(int operations) {
        NioUtils.clearInterestOps(selectionKey, operations);
    }

    public void disableReads() {
        disable(SelectionKey.OP_READ);
    }

    public void disableWrites() {
        disable(SelectionKey.OP_WRITE);
    }

    public void set(int operations) {
        selectionKey.interestOps(operations);
    }

    public void setNone() {
        set(0);
    }

    public void setAll() {
        set(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    public void setReadsOnly() {
        set(SelectionKey.OP_READ);
    }

    public void setWritesOnly() {
        set(SelectionKey.OP_WRITE);
    }
}
