package org.netcrusher.core.meter;

import java.io.Serializable;

/**
 * Sent and read statistics
 */
public class RateMeters implements Serializable {

    private final RateMeter readMeter;

    private final RateMeter sentMeter;

    public RateMeters(RateMeter readMeter, RateMeter sentMeter) {
        this.readMeter = readMeter;
        this.sentMeter = sentMeter;
    }

    /**
     * Request rate meter for reading
     * @return Rate meter
     */
    public RateMeter getReadMeter() {
        return readMeter;
    }

    /**
     * Request rate meter for writing
     * @return Rate meter
     */
    public RateMeter getSentMeter() {
        return sentMeter;
    }
}
