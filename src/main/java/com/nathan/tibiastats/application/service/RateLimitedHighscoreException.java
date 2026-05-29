package com.nathan.tibiastats.application.service;

class RateLimitedHighscoreException extends RuntimeException {
    RateLimitedHighscoreException(String message) {
        super(message);
    }

    RateLimitedHighscoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
