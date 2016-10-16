package org.netcrusher.datagram;

import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.throttle.Throttler;

public final class DatagramFilters {

    private final TransformFilter incomingTransformFilter;

    private final TransformFilter outgoingTransformFilter;

    private final PassFilter incomingPassFilter;

    private final PassFilter outgoingPassFilter;

    private final Throttler incomingThrottler;

    private final Throttler outgoingThrottler;

    public DatagramFilters(
            TransformFilter incomingTransformFilter, TransformFilter outgoingTransformFilter,
            PassFilter incomingPassFilter, PassFilter outgoingPassFilter,
            Throttler incomingThrottler, Throttler outgoingThrottler) {
        this.incomingTransformFilter = incomingTransformFilter;
        this.outgoingTransformFilter = outgoingTransformFilter;
        this.incomingPassFilter = incomingPassFilter;
        this.outgoingPassFilter = outgoingPassFilter;
        this.incomingThrottler = incomingThrottler;
        this.outgoingThrottler = outgoingThrottler;
    }

    public TransformFilter getIncomingTransformFilter() {
        return incomingTransformFilter;
    }

    public TransformFilter getOutgoingTransformFilter() {
        return outgoingTransformFilter;
    }

    public PassFilter getIncomingPassFilter() {
        return incomingPassFilter;
    }

    public PassFilter getOutgoingPassFilter() {
        return outgoingPassFilter;
    }

    public Throttler getIncomingThrottler() {
        return incomingThrottler;
    }

    public Throttler getOutgoingThrottler() {
        return outgoingThrottler;
    }
}

