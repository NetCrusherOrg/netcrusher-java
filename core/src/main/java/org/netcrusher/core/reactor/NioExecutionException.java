package org.netcrusher.core.reactor;

public class NioExecutionException extends RuntimeException {

    public NioExecutionException(String message) {
        super(message);
    }

    public NioExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
