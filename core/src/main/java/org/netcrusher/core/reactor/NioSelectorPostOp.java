package org.netcrusher.core.reactor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NioSelectorPostOp<T> implements Runnable {

    private final CompletableFuture<T> future;

    private final Callable<T> delegate;

    NioSelectorPostOp(Callable<T> delegate) {
        this.delegate = delegate;
        this.future = new CompletableFuture<>();
    }

    @Override
    public void run() {
        try {
            T result = delegate.call();
            future.complete(result);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    T await() throws InterruptedException, ExecutionException {
        return future.get();
    }

    T await(long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException, ExecutionException {
        return future.get(timeout, timeUnit);
    }
}
