package org.netcrusher.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NioReactor implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioReactor.class);

    private static final long THREAD_TERMINATION_TIMEOUT_MS = 5000;

    private final ScheduledExecutorService scheduledExecutorService;

    private final NioSelector selector;

    private volatile boolean open;

    public NioReactor() throws IOException {
        this.selector = new NioSelector();

        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r);
            thread.setName("NetCrusher scheduled executor");
            thread.setDaemon(false);
            return thread;
        });

        this.open = true;
        LOGGER.debug("Reactor has been created");
    }

    @Override
    public synchronized void close() {
        if (open) {
            return;
        }

        boolean interrupted = false;
        LOGGER.debug("Reactor is closing");

        scheduledExecutorService.shutdownNow();
        try {
            boolean shutdown = scheduledExecutorService
                .awaitTermination(THREAD_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!shutdown) {
                LOGGER.error("Fail to shutdown scheduled executor service");
            }
        } catch (InterruptedException e) {
            interrupted = true;
        }

        selector.close();

        open = false;
        LOGGER.debug("Reactor is closed");

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public Future<?> schedule(long delayMs, Callable<?> callable) {
        return scheduledExecutorService.schedule(callable, delayMs, TimeUnit.MILLISECONDS);
    }
    public Future<?> schedule(long delayMs, Runnable runnable) {
        return scheduledExecutorService.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
    }

    public Future<?> execute(Callable<?> callable) {
        return scheduledExecutorService.submit(callable);
    }

    public Future<?> execute(Runnable runnable) {
        return scheduledExecutorService.submit(runnable);
    }

    public NioSelector getSelector() {
        return selector;
    }
}
