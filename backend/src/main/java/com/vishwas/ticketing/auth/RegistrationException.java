package com.vishwas.ticketing.auth;

public final class RegistrationException extends RuntimeException {

    public enum Reason {
        INVALID_INPUT,
        DUPLICATE_EMAIL
    }

    private final Reason reason;

    public RegistrationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RegistrationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}