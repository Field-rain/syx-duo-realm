package com.syxduorealm.trade;

import java.time.Instant;

public final class TradeStatus {

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

    private TradeStatus(State state, Instant timestamp, String message, String error, Integer statusCode) {
        this.state = state;
        this.timestamp = timestamp;
        this.message = message;
        this.error = error;
        this.statusCode = statusCode;
    }

    public static TradeStatus notRun() {
        return new TradeStatus(State.NOT_RUN, null, "No trade inbox request has run yet.", null, null);
    }

    public static TradeStatus notRun(String message) {
        return new TradeStatus(State.NOT_RUN, null, message, null, null);
    }

    public static TradeStatus inFlight(String message) {
        return new TradeStatus(State.IN_FLIGHT, Instant.now(), message, null, null);
    }

    public static TradeStatus success(String message, int statusCode) {
        return new TradeStatus(State.SUCCESS, Instant.now(), message, null, statusCode);
    }

    public static TradeStatus failure(String message, Throwable throwable) {
        String error = throwable == null ? null : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        return new TradeStatus(State.FAILED, Instant.now(), message, error, null);
    }

    public static TradeStatus httpFailure(String message, int statusCode, String body) {
        String error = "HTTP " + statusCode;
        if (body != null && !body.isBlank()) {
            error += ": " + abbreviate(body.trim(), 160);
        }
        return new TradeStatus(State.FAILED, Instant.now(), message, error, statusCode);
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
