package org.netcrusher.core.state;

public class MultiBitState extends BitState {

    public MultiBitState(int state) {
        super(state);
    }

    public void include(int state) {
        set(get() | state);
    }

    public void exclude(int state) {
        set(get() & ~state);
    }

}
