package org.netcrusher.core.meter;

import java.util.Date;

public interface RateMeter {

    /**
     * Get meter's creation moment
     * @return Moment of creation
     */
    Date created();

    /**
     * Request total count (bytes, events, items)
     * @return Number of events for all time
     */
    long countTotal();

    /**
     * Request period count (bytes, events, items) - the number of events from the last rate() call
     * @param reset true if period should be reset
     * @return Number of events for the last period
     */
    long countPeriod(boolean reset);

    /**
     * Request period rate - the number of event per seconds from the last rate() call
     * @param reset true if period should be reset
     * @return Number per the last period
     */
    double ratePeriod(boolean reset);
}
