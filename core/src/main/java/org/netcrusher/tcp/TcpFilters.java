package org.netcrusher.tcp;

import org.netcrusher.core.filter.TransformFilter;

public class TcpFilters {

    private final TransformFilter incomingTransformFilter;

    private final TransformFilter outgoingTransformFilter;

    public TcpFilters(TransformFilter incomingTransformFilter, TransformFilter outgoingTransformFilter) {
        this.incomingTransformFilter = incomingTransformFilter;
        this.outgoingTransformFilter = outgoingTransformFilter;
    }

    public TransformFilter getIncomingTransformFilter() {
        return incomingTransformFilter;
    }

    public TransformFilter getOutgoingTransformFilter() {
        return outgoingTransformFilter;
    }
}

