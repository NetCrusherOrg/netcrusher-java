package org.netcrusher.datagram;

import org.netcrusher.core.filter.PassFilter;
import org.netcrusher.core.filter.TransformFilter;

public class DatagramFilters {

    private final TransformFilter incomingTransformFilter;

    private final TransformFilter outgoingTransformFilter;

    private final PassFilter incomingPassFilter;

    private final PassFilter outgoingPassFilter;

    public DatagramFilters(TransformFilter incomingTransformFilter,
                           TransformFilter outgoingTransformFilter,
                           PassFilter incomingPassFilter,
                           PassFilter outgoingPassFilter) {
        this.incomingTransformFilter = incomingTransformFilter;
        this.outgoingTransformFilter = outgoingTransformFilter;
        this.incomingPassFilter = incomingPassFilter;
        this.outgoingPassFilter = outgoingPassFilter;
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
}

