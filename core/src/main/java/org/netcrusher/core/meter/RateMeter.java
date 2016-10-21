package org.netcrusher.core.meter;

/**
 * Total and period statistics (number of events and time)
 */
public interface RateMeter {

    /**
     * Request total count (bytes, events, items)
     * @return Number of events for all time
     */
    long getTotalCount();

    /**
     * Get elapsed time from the moment of creation
     * @return Elapsed time in milliseconds
     */
    long getTotalElapsedMs();

    /**
     * Get total count and elapsed time
     * @return Total statistics
     */
    RateMeterPeriod getTotal();

    /**
     * Request period rate - the number of event per seconds from the last reset
     * @param reset true if period should be reset
     * @return Period statistics
     */
    RateMeterPeriod getPeriod(boolean reset);
}
