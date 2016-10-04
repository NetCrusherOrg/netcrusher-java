package org.netcrusher.core;

import org.netcrusher.NetCrusher;
import org.netcrusher.NetFreezer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NioScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioScheduler.class);

    private static final long THREAD_TERMINATION_TIMEOUT_MS = 5000;

    private final ScheduledExecutorService scheduledExecutorService;

    private volatile boolean open;

    NioScheduler() {
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r);
            thread.setName("NetCrusher scheduled executor");
            thread.setDaemon(false);
            return thread;
        });

        this.open = true;
    }

    synchronized void close() {
        if (open) {
            return;
        }

        boolean interrupted = false;
        LOGGER.debug("Scheduler is closing");

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

        open = false;
        LOGGER.debug("Scheduler is closed");

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute callable instance in scheduler's thread immediately
     * @param callable Callable instance
     * @return The future
     */
    public Future<?> execute(Callable<?> callable) {
        return scheduledExecutorService.submit(callable);
    }

    /**
     * Execute runnable instance in scheduler's thread immediately
     * @param runnable Runnable instance
     * @return The future
     */
    public Future<?> execute(Runnable runnable) {
        return scheduledExecutorService.submit(runnable);
    }

    /**
     * Schedule callable instance to execute in scheduler's thread
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param callable Callable instance
     * @return The future
     */
    public Future<?> schedule(long delay, TimeUnit timeUnit, Callable<?> callable) {
        return scheduledExecutorService.schedule(callable, delay, timeUnit);
    }

    /**
     * Schedule runnable instance to execute in scheduler's thread
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param runnable Runnable instance
     * @return The future
     */
    public Future<?> schedule(long delay, TimeUnit timeUnit, Runnable runnable) {
        return scheduledExecutorService.schedule(runnable, delay, timeUnit);
    }

    /**
     * Schedule freeze
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param freezer Freezer instance
     * @return The future
     */
    public Future<?> scheduleFreeze(long delay, TimeUnit timeUnit, NetFreezer freezer) {
        return scheduledExecutorService.schedule(() -> {
            freezer.freeze();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule unfreeze
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param freezer Freezer instance
     * @return The future
     */
    public Future<?> scheduleUnfreeze(long delay, TimeUnit timeUnit, NetFreezer freezer) {
        return scheduledExecutorService.schedule(() -> {
            freezer.unfreeze();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule opening
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param crusher Crusher instance
     * @return The future
     */
    public Future<?> scheduleOpen(long delay, TimeUnit timeUnit, NetCrusher crusher) {
        return scheduledExecutorService.schedule(() -> {
            crusher.crush();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule closing
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param crusher Crusher instance
     * @return The future
     */
    public Future<?> scheduleClose(long delay, TimeUnit timeUnit, NetCrusher crusher) {
        return scheduledExecutorService.schedule(() -> {
            crusher.close();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule crushing
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @param crusher Crusher instance
     * @return The future
     */
    public Future<?> scheduleCrush(long delay, TimeUnit timeUnit, NetCrusher crusher) {
        return scheduledExecutorService.schedule(() -> {
            crusher.crush();
            return true;
        }, delay, timeUnit);
    }

}
