package org.netcrusher;

public class NetCrusherException extends RuntimeException {

    public NetCrusherException(String message) {
        super(message);
    }

    public NetCrusherException(String message, Throwable cause) {
        super(message, cause);
    }

}
