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

    private static final long THREAD_TERMINATION_TIMEOUT_MS = 1000;

    private final Thread thread;

    private final Selector selector;

    private final ScheduledExecutorService scheduledExecutorService;

    private final Queue<NioReactorOp<?>> ops;

    private volatile boolean opened;

    public NioReactor() throws IOException {
        this.selector = Selector.open();
        this.ops = new ConcurrentLinkedQueue<>();

        this.thread = new Thread(this::loop);
        this.thread.setName("TcpCrusher context event loop");
        this.thread.setDaemon(false);
        this.thread.start();

        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r);
            thread.setName("TcpCrusher scheduled executor");
            thread.setDaemon(false);
            return thread;
        });

        this.opened = true;
        LOGGER.debug("Context has been created");
    }

    @Override
    public synchronized void close() {
        if (!opened) {
            return;
        }

        boolean interrupted = false;
        LOGGER.debug("Context is closing");

        if (thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(THREAD_TERMINATION_TIMEOUT_MS);
            } catch (InterruptedException e) {
                interrupted = true;
            }

            if (thread.isAlive()) {
                LOGGER.error("TcpCrusher event thread is still alive");
            }
        }

        scheduledExecutorService.shutdown();
        try {
            boolean shutdown = scheduledExecutorService
                .awaitTermination(THREAD_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!shutdown) {
                LOGGER.error("Fail to shutdown scheduled executor service");
            }
        } catch (InterruptedException e) {
            interrupted = true;
        }

        try {
            this.selector.close();
        } catch (IOException e) {
            LOGGER.error("Fail to close selector", e);
        }

        this.opened = false;
        LOGGER.debug("Context is closed");

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public SelectionKey register(SelectableChannel channel, int options, SelectionKeyCallback callback)
            throws IOException
    {
        return executeReactorOp(() -> channel.register(selector, options, callback));
    }

    public void wakeup() throws IOException {
        // fixes some strange behaviour on Windows: http://stackoverflow.com/a/39657002/827139
        executeReactorOp(selector::selectNow);
    }

    public <T> T executeReactorOp(Callable<T> callable) throws IOException {
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
                return op.getFuture().get();
            } catch (InterruptedException e) {
                throw new InterruptedIOException("Registering on selector was interrupted");
            } catch (ExecutionException e) {
                throw new IOException("Registering on selector failed", e);
            }
        }
    }

    public Future<?> schedule(long delayMs, Runnable runnable) {
        return scheduledExecutorService.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
    }

    public Future<?> execute(Runnable runnable) {
        return scheduledExecutorService.submit(runnable);
    }

    private void loop() {
        LOGGER.debug("Context event loop started");

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

        LOGGER.debug("Context event loop has finished");
    }

    public boolean isOpened() {
        return opened;
    }
}
