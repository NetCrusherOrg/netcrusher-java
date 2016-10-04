package org.netcrusher.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class NioReactor implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioReactor.class);

    private final NioSelector selector;

    private final NioScheduler scheduler;

    private volatile boolean open;

    public NioReactor() throws IOException {
        this.selector = new NioSelector();
        this.scheduler = new NioScheduler();
        this.open = true;

        LOGGER.debug("Reactor has been created");
    }

    @Override
    public synchronized void close() {
        if (open) {
            return;
        }

        selector.close();
        scheduler.close();

        open = false;
        LOGGER.debug("Reactor is closed");
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
