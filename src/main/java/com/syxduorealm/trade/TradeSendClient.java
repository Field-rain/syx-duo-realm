package com.syxduorealm.trade;

import com.syxduorealm.config.JsonObjectParser;
import com.syxduorealm.config.SyxDuoRealmConfig;
import com.syxduorealm.export.CityStateJsonWriter;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TradeSendClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new SendThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final AtomicBoolean sendInFlight = new AtomicBoolean(false);
    private final AtomicReference<TradeStatus> lastStatus = new AtomicReference<>(TradeStatus.notRun("No trade send request has run yet."));
    private final AtomicReference<String> lastTradeId = new AtomicReference<>("");

    public TradeStatus sendAsync(
        SyxDuoRealmConfig config,
        String tradeId,
        String toPlayer,
        String resourceKey,
        int amount,
        int available
    ) {
        if (!sendInFlight.compareAndSet(false, true)) {
            TradeStatus status = TradeStatus.inFlight("Trade send request already in progress.");
            lastStatus.set(status);
            return status;
        }

        TradeStatus started = TradeStatus.inFlight("Sending trade offer.");
        lastStatus.set(started);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tradeId", tradeId);
            body.put("roomCode", config.roomCode());
            body.put("fromPlayer", config.playerName());
            body.put("toPlayer", toPlayer);
            body.put("resourceKey", resourceKey);
            body.put("amount", amount);
            body.put("availableAtSend", available);
            body.put("source", "game-client");

            HttpRequest request = HttpRequest.newBuilder(tradeUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeSend, executor);
        } catch (Throwable throwable) {
            sendInFlight.set(false);
            TradeStatus failed = TradeStatus.failure("Could not start trade send request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public TradeStatus lastStatus() {
        return lastStatus.get();
    }

    public String lastTradeId() {
        return lastTradeId.get();
    }

    private void completeSend(HttpResponse<String> response, Throwable throwable) {
        sendInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            TradeStatus failed = TradeStatus.failure("Trade send request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            TradeStatus failed = TradeStatus.httpFailure("Server rejected trade send request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] trade send FAILED: " + failed.error());
            return;
        }

        String tradeId = parseTradeId(response.body());
        lastTradeId.set(tradeId);
        TradeStatus status = TradeStatus.success(tradeId.isBlank() ? "Trade offer sent." : "Trade offer sent: " + tradeId, statusCode);
        lastStatus.set(status);
        System.out.println("[Syx Duo Realm] trade send SUCCESS: " + status.message());
    }

    private String parseTradeId(String body) {
        try {
            Map<String, Object> json = JsonObjectParser.parse(body);
            Object tradeValue = json.get("trade");
            if (tradeValue instanceof Map<?, ?> trade) {
                Object tradeId = trade.get("tradeId");
                return tradeId == null ? "" : String.valueOf(tradeId);
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private URI tradeUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/trade", null, null);
    }

    private String apiBasePath(URI server) {
        String path = server.getPath();
        if (path == null || path.isBlank()) {
            return "/api";
        }
        if (path.endsWith("/state")) {
            return path.substring(0, path.length() - "/state".length());
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void logFailure(TradeStatus status, Throwable throwable) {
        System.err.println("[Syx Duo Realm] trade send FAILED: " + status.message());
        if (status.error() != null) {
            System.err.println("[Syx Duo Realm] trade send error: " + status.error());
        }
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static final class SendThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Syx Duo Realm Trade Send HTTP");
            thread.setDaemon(true);
            return thread;
        }
    }
}
