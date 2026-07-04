package com.syxduorealm.sync;

import java.time.Instant;

public final class SyncStatus {

    public enum State {
        NOT_RUN,
        IN_FLIGHT,
        SUCCESS,
        FAILED
    }

    private final State state;
    private final Instant timestamp;
    private final String serverUrl;
    private final String message;
    private final String error;
    private final Integer statusCode;

    private SyncStatus(
        State state,
        Instant timestamp,
        String serverUrl,
        String message,
        String error,
        Integer statusCode
    ) {
        this.state = state;
        this.timestamp = timestamp;
        this.serverUrl = serverUrl;
        this.message = message;
        this.error = error;
        this.statusCode = statusCode;
    }

    public static SyncStatus notRun(String serverUrl) {
        return new SyncStatus(State.NOT_RUN, null, serverUrl, "No HTTP sync has run yet.", null, null);
    }

    public static SyncStatus inFlight(String serverUrl) {
        return new SyncStatus(State.IN_FLIGHT, Instant.now(), serverUrl, "HTTP sync in progress.", null, null);
    }

    public static SyncStatus skipped(String serverUrl) {
        return new SyncStatus(State.IN_FLIGHT, Instant.now(), serverUrl, "Previous HTTP sync is still in progress.", null, null);
    }

    public static SyncStatus success(String serverUrl, int statusCode) {
        return new SyncStatus(State.SUCCESS, Instant.now(), serverUrl, "HTTP sync completed.", null, statusCode);
    }

    public static SyncStatus httpFailure(String serverUrl, int statusCode, String body) {
        String error = "HTTP " + statusCode;
        if (body != null && !body.isBlank()) {
            error += ": " + abbreviate(body.trim(), 160);
        }
        return new SyncStatus(State.FAILED, Instant.now(), serverUrl, "Server rejected city state.", error, statusCode);
    }

    public static SyncStatus failure(String serverUrl, String message, Throwable throwable) {
        String error = throwable == null ? null : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        return new SyncStatus(State.FAILED, Instant.now(), serverUrl, message, error, null);
    }

    public State state() {
        return state;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String serverUrl() {
        return serverUrl;
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

    public String notificationKey() {
        return state + "|" + statusCode + "|" + error;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
