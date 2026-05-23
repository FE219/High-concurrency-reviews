package com.hmdp.exception;

public class AiServiceException extends RuntimeException {
    private final boolean circuitOpen;

    public AiServiceException(String message, boolean circuitOpen) {
        super(message);
        this.circuitOpen = circuitOpen;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.circuitOpen = false;
    }

    public boolean isCircuitOpen() { return circuitOpen; }
}
