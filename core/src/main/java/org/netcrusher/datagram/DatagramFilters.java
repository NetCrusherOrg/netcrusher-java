package org.netcrusher.datagram;

import org.netcrusher.core.filter.PassFilterFactory;
import org.netcrusher.core.filter.TransformFilterFactory;
import org.netcrusher.core.throttle.Throttler;
import org.netcrusher.core.throttle.ThrottlerFactory;

public final class DatagramFilters {

    private final TransformFilterFactory incomingTransformFilterFactory;

    private final TransformFilterFactory outgoingTransformFilterFactory;

    private final PassFilterFactory incomingPassFilterFactory;

    private final PassFilterFactory outgoingPassFilterFactory;

    private final Throttler incomingThrottler;

    private final ThrottlerFactory outgoingThrottlerFactory;

    public DatagramFilters(
        TransformFilterFactory incomingTransformFilterFactory,
        TransformFilterFactory outgoingTransformFilterFactory,
        PassFilterFactory incomingPassFilterFactory,
        PassFilterFactory outgoingPassFilterFactory,
        Throttler incomingThrottler,
        ThrottlerFactory outgoingThrottlerFactory)
    {
        this.incomingTransformFilterFactory = incomingTransformFilterFactory;
        this.outgoingTransformFilterFactory = outgoingTransformFilterFactory;
        this.incomingPassFilterFactory = incomingPassFilterFactory;
        this.outgoingPassFilterFactory = outgoingPassFilterFactory;
        this.incomingThrottler = incomingThrottler;
        this.outgoingThrottlerFactory = outgoingThrottlerFactory;
    }

    public TransformFilterFactory getIncomingTransformFilterFactory() {
        return incomingTransformFilterFactory;
    }

    public TransformFilterFactory getOutgoingTransformFilterFactory() {
        return outgoingTransformFilterFactory;
    }

    public PassFilterFactory getIncomingPassFilterFactory() {
        return incomingPassFilterFactory;
    }

    public PassFilterFactory getOutgoingPassFilterFactory() {
        return outgoingPassFilterFactory;
    }

    public Throttler getIncomingThrottler() {
        return incomingThrottler;
    }

    public ThrottlerFactory getOutgoingThrottlerFactory() {
        return outgoingThrottlerFactory;
    }
}

