package org.netcrusher.core.meter;

public interface RateMeter {

    /**
     * Get elapsed time from the moment of creation
     * @return Elapsed time in milliseconds
     */
    long getTotalElapsedMs();

    /**
     * Request total count (bytes, events, items)
     * @return Number of events for all time
     */
    long getTotalCount();

    /**
     * Request period count (bytes, events, items) - the number of events from the last rate() call
     * @param reset true if period should be reset
     * @return Number of events for the last period
     */
    long getPeriodCount(boolean reset);

    /**
     * Request period rate - the number of event per seconds from the last rate() call
     * @param reset true if period should be reset
     * @return Number per the last period
     */
    double getPeriodRate(boolean reset);
}
