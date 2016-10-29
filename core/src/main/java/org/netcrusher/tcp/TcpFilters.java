package org.netcrusher.tcp;

import org.netcrusher.core.filter.TransformFilter;
import org.netcrusher.core.throttle.Throttler;

public class TcpFilters {

    private final TransformFilter incomingTransformFilter;

    private final TransformFilter outgoingTransformFilter;

    private final Throttler incomingThrottler;

    private final Throttler outgoingThrottler;

    public TcpFilters(
            TransformFilter incomingTransformFilter, TransformFilter outgoingTransformFilter,
            Throttler incomingThrottler, Throttler outgoingThrottler)
    {
        this.incomingTransformFilter = incomingTransformFilter;
        this.outgoingTransformFilter = outgoingTransformFilter;
        this.incomingThrottler = incomingThrottler;
        this.outgoingThrottler = outgoingThrottler;
    }

    public TransformFilter getIncomingTransformFilter() {
        return incomingTransformFilter;
    }

    public TransformFilter getOutgoingTransformFilter() {
        return outgoingTransformFilter;
    }

    public Throttler getIncomingThrottler() {
        return incomingThrottler;
    }

    public Throttler getOutgoingThrottler() {
        return outgoingThrottler;
    }
}

