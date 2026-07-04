package com.syxduorealm.export;

import java.nio.file.Path;
import java.time.Instant;

public final class ExportStatus {

    public enum State {
        NOT_RUN,
        SUCCESS,
        FAILED
    }

    private final State state;
    private final Instant timestamp;
    private final Path outputPath;
    private final String message;
    private final String error;

    private ExportStatus(State state, Instant timestamp, Path outputPath, String message, String error) {
        this.state = state;
        this.timestamp = timestamp;
        this.outputPath = outputPath;
        this.message = message;
        this.error = error;
    }

    public static ExportStatus notRun(Path outputPath) {
        return new ExportStatus(State.NOT_RUN, null, outputPath, "No export has run yet.", null);
    }

    public static ExportStatus success(Path outputPath, String message) {
        return new ExportStatus(State.SUCCESS, Instant.now(), outputPath, message, null);
    }

    public static ExportStatus failure(Path outputPath, String message, Throwable throwable) {
        String error = throwable == null ? null : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        return new ExportStatus(State.FAILED, Instant.now(), outputPath, message, error);
    }

    public State state() {
        return state;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public Path outputPath() {
        return outputPath;
    }

    public String message() {
        return message;
    }

    public String error() {
        return error;
    }

    public String shortLabel() {
        return switch (state) {
            case NOT_RUN -> "DUO --";
            case SUCCESS -> "DUO OK";
            case FAILED -> "DUO ERR";
        };
    }
}
