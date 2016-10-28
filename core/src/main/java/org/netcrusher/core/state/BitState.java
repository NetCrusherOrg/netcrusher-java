package org.netcrusher.core.state;

import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BitState implements Serializable {

    private final Lock lock;

    private volatile int state;

    public BitState(int state) {
        this.state = state;
        this.lock = new ReentrantLock(true);
    }
}
