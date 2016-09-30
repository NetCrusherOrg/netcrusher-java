package org.netcrusher.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NioReactor implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioReactor.class);

    private static final long THREAD_TERMINATION_TIMEOUT_MS = 5000;

    private final Thread thread;

    private final Selector selector;

    private final ScheduledExecutorService scheduledExecutorService;

    private final Queue<NioReactorOp<?>> ops;

    private volatile boolean open;

    public NioReactor() throws IOException {
        this.selector = Selector.open();
        this.ops = new ConcurrentLinkedQueue<>();

        this.thread = new Thread(this::loop);
        this.thread.setName("NetCrusher reactor event loop");
        this.thread.setDaemon(false);
        this.thread.start();

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
    public synchronized void close() throws IOException {
        if (!open) {
            return;
        }

        boolean interrupted = false;
        LOGGER.debug("Reactor is closing");

        ops.clear();

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

        wakeupSelector();

        if (thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(THREAD_TERMINATION_TIMEOUT_MS);
            } catch (InterruptedException e) {
                interrupted = true;
            }

            if (thread.isAlive()) {
                LOGGER.error("NetCrusher reactor thread is still alive");
            }
        }

        int activeSelectionKeys = selector.keys().size();
        if (activeSelectionKeys > 0) {
            LOGGER.warn("Selector still has {} selection keys. Have you closed all linked crushers before?",
                activeSelectionKeys);
        }

        try {
            selector.close();
        } catch (IOException e) {
            LOGGER.error("Fail to close selector", e);
        }

        open = false;
        LOGGER.debug("Reactor is closed");

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public SelectionKey registerSelector(SelectableChannel channel, int options, SelectionKeyCallback callback)
            throws IOException {
        return executeSelectorOp(() -> channel.register(selector, options, callback));
    }

    public void wakeupSelector() throws IOException {
        // fixes some strange behaviour on Windows: http://stackoverflow.com/a/39657002/827139
        executeSelectorOp(selector::selectNow);
    }

    public <T> T executeSelectorOp(Callable<T> callable) throws IOException {
        if (Thread.currentThread().equals(thread)) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new IOException("Fail to execute reactor op", e);
            }
        } else {
            NioReactorOp<T> op = new NioReactorOp<>(callable);
            ops.add(op);

            selector.wakeup();

            try {
                return op.await();
            } catch (InterruptedException e) {
                throw new InterruptedIOException("Reactor operation was interrupted");
            } catch (ExecutionException e) {
                throw new IOException("Reactor operation has failed", e);
            }
        }
    }

    public void executeSelectorOp(Runnable runnable) throws IOException {
        executeSelectorOp(() -> {
            runnable.run();
            return null;
        });
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

    private void loop() {
        LOGGER.debug("Reactor event loop started");

        while (!Thread.currentThread().isInterrupted()) {
            int count;
            try {
                count = selector.select();
            } catch (ClosedSelectorException e) {
                break;
            } catch (IOException e) {
                LOGGER.error("Error on select", e);
                break;
            }

            if (count > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();

                Iterator<SelectionKey> keyIterator = keys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey selectionKey = keyIterator.next();

                    if (selectionKey.isValid()) {
                        SelectionKeyCallback callback = (SelectionKeyCallback) selectionKey.attachment();
                        try {
                            callback.execute(selectionKey);
                        } catch (Exception e) {
                            LOGGER.error("Error while executing selection key callback", e);
                        }
                    }

                    keyIterator.remove();
                }
            }

            NioReactorOp<?> op;
            while ((op = ops.poll()) != null) {
                op.run();
            }
        }

        LOGGER.debug("Reactor event loop has finished");
    }

}
