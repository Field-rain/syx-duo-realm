package com.syxduorealm.war;

import java.time.Instant;

public final class WarStatus {

    public enum State {
        NOT_RUN,
        IN_FLIGHT,
        SUCCESS,
        FAILED
    }

    private final State state;
    private final Instant timestamp;
    private final String message;
    private final String error;
    private final Integer statusCode;

    private WarStatus(State state, Instant timestamp, String message, String error, Integer statusCode) {
        this.state = state;
        this.timestamp = timestamp;
        this.message = message;
        this.error = error;
        this.statusCode = statusCode;
    }

    public static WarStatus notRun() {
        return new WarStatus(State.NOT_RUN, null, "No war request has run yet.", null, null);
    }

    public static WarStatus inFlight(String message) {
        return new WarStatus(State.IN_FLIGHT, Instant.now(), message, null, null);
    }

    public static WarStatus success(String message, int statusCode) {
        return new WarStatus(State.SUCCESS, Instant.now(), message, null, statusCode);
    }

    public static WarStatus failure(String message, Throwable throwable) {
        String error = throwable == null ? null : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        return new WarStatus(State.FAILED, Instant.now(), message, error, null);
    }

    public static WarStatus httpFailure(String message, int statusCode, String body) {
        String error = "HTTP " + statusCode;
        if (body != null && !body.isBlank()) {
            error += ": " + abbreviate(body.trim(), 160);
        }
        return new WarStatus(State.FAILED, Instant.now(), message, error, statusCode);
    }

    public State state() {
        return state;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String message() {
        return message;
    }

    public String error() {
        return error;
    }

    public Integer statusCode() {
        return statusCode;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
