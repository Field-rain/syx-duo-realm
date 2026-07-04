package com.syxduorealm.sync;

import com.syxduorealm.config.SyxDuoRealmConfig;
import com.syxduorealm.export.CityState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SyncClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new SyncThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicReference<SyncStatus> lastStatus;

    public SyncClient(String serverUrl) {
        lastStatus = new AtomicReference<>(SyncStatus.notRun(serverUrl));
    }

    public SyncStatus syncAsync(SyxDuoRealmConfig config, CityState state) {
        if (!inFlight.compareAndSet(false, true)) {
            SyncStatus skipped = SyncStatus.skipped(config.serverUrl());
            lastStatus.set(skipped);
            return skipped;
        }

        SyncStatus started = SyncStatus.inFlight(config.serverUrl());
        lastStatus.set(started);

        try {
            String payload = SyncPayloadWriter.write(config, state);
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.serverUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "Syx-Duo-Realm/0.2")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync((response, throwable) -> complete(config.serverUrl(), response, throwable), executor);
        } catch (Throwable throwable) {
            inFlight.set(false);
            SyncStatus failed = SyncStatus.failure(config.serverUrl(), "Could not start HTTP sync.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public SyncStatus lastStatus() {
        return lastStatus.get();
    }

    private void complete(String serverUrl, HttpResponse<String> response, Throwable throwable) {
        inFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            SyncStatus failed = SyncStatus.failure(serverUrl, "HTTP sync failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            SyncStatus success = SyncStatus.success(serverUrl, statusCode);
            lastStatus.set(success);
            System.out.println("[Syx Duo Realm] sync SUCCESS: HTTP " + statusCode + " -> " + serverUrl);
            return;
        }

        SyncStatus failed = SyncStatus.httpFailure(serverUrl, statusCode, response.body());
        lastStatus.set(failed);
        System.err.println("[Syx Duo Realm] sync FAILED: " + failed.error() + " -> " + serverUrl);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void logFailure(SyncStatus status, Throwable throwable) {
        System.err.println("[Syx Duo Realm] sync FAILED: " + status.message() + " -> " + status.serverUrl());
        if (status.error() != null) {
            System.err.println("[Syx Duo Realm] sync error: " + status.error());
        }
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static final class SyncThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Syx Duo Realm HTTP Sync");
            thread.setDaemon(true);
            return thread;
        }
    }
}
