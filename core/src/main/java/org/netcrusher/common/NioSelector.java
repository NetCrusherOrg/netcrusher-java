package org.netcrusher.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class NioSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioSelector.class);

    private static final long THREAD_TERMINATION_TIMEOUT_MS = 5000;

    private final Thread thread;

    private final Selector selector;

    private final Queue<NioSelectorOp<?>> ops;

    private volatile boolean open;

    NioSelector() throws IOException {
        this.selector = Selector.open();
        this.ops = new ConcurrentLinkedQueue<>();

        this.thread = new Thread(this::loop);
        this.thread.setName("NetCrusher selector event loop");
        this.thread.setDaemon(false);
        this.thread.start();

        this.open = true;
    }

    synchronized void close() {
        if (!open) {
            return;
        }

        LOGGER.debug("Selector is closing");
        boolean interrupted = false;

        ops.clear();

        try {
            wakeup();
        } catch (IOException e) {
            LOGGER.error("Fail to wake up selector", e);
        }

        if (thread.isAlive()) {
            thread.interrupt();

            try {
                thread.join(THREAD_TERMINATION_TIMEOUT_MS);
            } catch (InterruptedException e) {
                interrupted = true;
            }

            if (thread.isAlive()) {
                LOGGER.error("NetCrusher selector thread is still alive");
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
        LOGGER.debug("Selector is closed");

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal method
     */
    public SelectionKey register(SelectableChannel channel, int options,
                                SelectionKeyCallback callback) throws IOException {
        return executeOp(() -> channel.register(selector, options, callback));
    }

    /**
     * Internal method
     */
    public int wakeup() throws IOException {
        // fixes some strange behaviour on Windows: http://stackoverflow.com/a/39657002/827139
        return executeOp(selector::selectNow);
    }

    /**
     * Internal method
     */
    public <T> T executeOp(Callable<T> callable) throws IOException {
        if (!open) {
            throw new IllegalStateException("Selector is closed");
        }

        if (Thread.currentThread().equals(thread)) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new IOException("Fail to execute selector op", e);
            }
        } else {
            NioSelectorOp<T> op = new NioSelectorOp<>(callable);
            ops.add(op);

            selector.wakeup();

            try {
                return op.await();
            } catch (InterruptedException e) {
                throw new InterruptedIOException("Reactor operation was interrupted");
            } catch (ExecutionException e) {
                throw new IOException("Selector operation has failed", e);
            }
        }
    }

    private void loop() {
        LOGGER.debug("Selector event loop started");

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

            NioSelectorOp<?> op;
            while ((op = ops.poll()) != null) {
                op.run();
            }
        }

        LOGGER.debug("Selector event loop has finished");
    }

}
