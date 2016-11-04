package org.netcrusher.tcp;

import org.netcrusher.core.filter.TransformFilterFactory;
import org.netcrusher.core.throttle.ThrottlerFactory;

public class TcpFilters {

    private final TransformFilterFactory incomingTransformFilterFactory;

    private final TransformFilterFactory outgoingTransformFilterFactory;

    private final ThrottlerFactory incomingThrottlerFactory;

    private final ThrottlerFactory outgoingThrottlerFactory;

    public TcpFilters(
        TransformFilterFactory incomingTransformFilterFactory,
        TransformFilterFactory outgoingTransformFilterFactory,
        ThrottlerFactory incomingThrottlerFactory,
        ThrottlerFactory outgoingThrottlerFactory)
    {
        this.incomingTransformFilterFactory = incomingTransformFilterFactory;
        this.outgoingTransformFilterFactory = outgoingTransformFilterFactory;
        this.incomingThrottlerFactory = incomingThrottlerFactory;
        this.outgoingThrottlerFactory = outgoingThrottlerFactory;
    }

    public TransformFilterFactory getIncomingTransformFilterFactory() {
        return incomingTransformFilterFactory;
    }

    public TransformFilterFactory getOutgoingTransformFilterFactory() {
        return outgoingTransformFilterFactory;
    }

    public ThrottlerFactory getIncomingThrottlerFactory() {
        return incomingThrottlerFactory;
    }

    public ThrottlerFactory getOutgoingThrottlerFactory() {
        return outgoingThrottlerFactory;
    }
}

