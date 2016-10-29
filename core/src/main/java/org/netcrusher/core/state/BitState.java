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

    protected static int bit(int num) {
        return 1 << num;
    }

    public int get() {
        return state;
    }

    public void set(int state) {
        this.state = state;
    }

    public boolean is(int state) {
        return this.state == state;
    }

    public boolean not(int state) {
        return this.state != state;
    }

    public boolean isAnyOf(int state) {
        return (this.state & state) != 0;
    }

    public boolean isNotOf(int state) {
        return (this.state & state) == 0;
    }

    public void unlock() {
        this.lock.unlock();
    }

    public void lock() {
        this.lock.lock();
    }

    public boolean lockIf(int state) {
        if (is(state)) {
            lock.lock();
            if (is(state)) {
                return true;
            } else {
                lock.unlock();
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean lockIfNot(int state) {
        if (not(state)) {
            lock.lock();
            if (not(state)) {
                return true;
            } else {
                lock.unlock();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "state=" + state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BitState that = (BitState) o;

        return this.state == that.state;
    }

    @Override
    public int hashCode() {
        return state;
    }
}
