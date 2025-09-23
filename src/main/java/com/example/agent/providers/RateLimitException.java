package com.example.agent.providers;

/**
 * Indicates that a request exceeded the provider rate limit and cannot be retried.
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
