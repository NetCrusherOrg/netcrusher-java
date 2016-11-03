package org.netcrusher.core.reactor;

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
     * Executes listener
     * @param runnable Listener method
     * @param deferred Set true if the listener should be called in a separate thread
     */
    public void executeListener(Runnable runnable, boolean deferred) {
        if (deferred) {
            execute(runnable);
        } else {
            try {
                runnable.run();
            } catch (Exception e) {
                LOGGER.error("Exception in listener", e);
            }
        }
    }

    /**
     * Schedule callable instance to execute in scheduler's thread
     * @param callable Callable instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> schedule(Callable<?> callable, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(callable, delay, timeUnit);
    }

    /**
     * Schedule runnable instance to execute in scheduler's thread
     * @param runnable Runnable instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(runnable, delay, timeUnit);
    }

    /**
     * Schedule freeze
     * @param freezer Freezer instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> scheduleFreeze(NetFreezer freezer, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(() -> {
            freezer.freeze();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule unfreeze
     * @param freezer Freezer instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> scheduleUnfreeze(NetFreezer freezer, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(() -> {
            freezer.unfreeze();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule opening
     * @param crusher Crusher instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> scheduleOpen(NetCrusher crusher, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(() -> {
            crusher.reopen();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule closing
     * @param crusher Crusher instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> scheduleClose(NetCrusher crusher, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(() -> {
            crusher.close();
            return true;
        }, delay, timeUnit);
    }

    /**
     * Schedule crushing (closing and reopening)
     * @param crusher Crusher instance
     * @param delay Execution delay
     * @param timeUnit Time unit
     * @return The future
     */
    public Future<?> scheduleReopen(NetCrusher crusher, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(() -> {
            crusher.reopen();
            return true;
        }, delay, timeUnit);
    }

}
