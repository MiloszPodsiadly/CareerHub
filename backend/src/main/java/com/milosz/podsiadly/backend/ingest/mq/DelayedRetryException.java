package com.milosz.podsiadly.backend.ingest.mq;

public class DelayedRetryException extends RuntimeException {

    private final long delayMs;

    public DelayedRetryException(String message, long delayMs) {
        super(message);
        this.delayMs = delayMs;
    }

    public DelayedRetryException(String message, long delayMs, Throwable cause) {
        super(message, cause);
        this.delayMs = delayMs;
    }

    public long getDelayMs() {
        return delayMs;
    }
}