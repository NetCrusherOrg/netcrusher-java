package org.netcrusher.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class NioReactor implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioReactor.class);

    private static final long DEFAULT_TICK_MS = 50;

    private final NioSelector selector;

    private final NioScheduler scheduler;

    private volatile boolean open;

    /**
     * Create NIO reactor with default settings. The same reactor can be shared across multiple crushers.
     * @throws IOException Exception on error
     */
    public NioReactor() throws IOException {
        this(DEFAULT_TICK_MS);
    }

    /**
     * Create NIO reactor with specific settings. The same reactor can be shared across multiple crushers.
     * @param tickMs Selector's timeout granularity in milliseconds. Determines throttling precision.
     *               Default value is 50 milliseconds.
     * @throws IOException Exception on error
     */
    public NioReactor(long tickMs) throws IOException {
        this.selector = new NioSelector(tickMs);
        this.scheduler = new NioScheduler();

        this.open = true;

        LOGGER.debug("Reactor has been created");
    }

    /**
     * Closes the reactor. After the reactor is closed it cannot be used.
     */
    @Override
    public synchronized void close() {
        if (open) {
            selector.close();
            scheduler.close();

            open = false;

            LOGGER.debug("Reactor is closed");
        }
    }

    /**
     * Check is the reactor open
     * @return Returns 'true' if the reactor is not closed
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Get selector controller (used for internal purpose)
     * @return Selector controller
     */
    public NioSelector getSelector() {
        return selector;
    }

    /**
     * Get scheduler controller
     * @return Schedule controller
     */
    public NioScheduler getScheduler() {
        return scheduler;
    }
}
