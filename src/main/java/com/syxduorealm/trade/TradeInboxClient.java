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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TradeInboxClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new TradeThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicReference<List<TradePackage>> inbox = new AtomicReference<>(List.of());
    private final AtomicReference<TradeStatus> lastStatus = new AtomicReference<>(TradeStatus.notRun());

    public TradeStatus refreshAsync(SyxDuoRealmConfig config, Set<String> claimedTradeIds) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            TradeStatus status = TradeStatus.inFlight("Trade inbox request already in progress.");
            lastStatus.set(status);
            return status;
        }

        TradeStatus started = TradeStatus.inFlight("Refreshing trade inbox.");
        lastStatus.set(started);

        try {
            HttpRequest request = HttpRequest.newBuilder(inboxUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync((response, throwable) -> completeRefresh(response, throwable, claimedTradeIds), executor);
        } catch (Throwable throwable) {
            refreshInFlight.set(false);
            TradeStatus failed = TradeStatus.failure("Could not start trade inbox request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public TradeStatus postClaimAsync(SyxDuoRealmConfig config, TradePackage trade) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("playerName", config.playerName());
            body.put("roomCode", config.roomCode());
            body.put("tradeId", trade.tradeId());

            HttpRequest request = HttpRequest.newBuilder(claimUri(config, trade.tradeId()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync((response, throwable) -> completeClaim(trade.tradeId(), response, throwable), executor);
            return TradeStatus.inFlight("Posting trade claim.");
        } catch (Throwable throwable) {
            TradeStatus failed = TradeStatus.failure("Could not start trade claim request.", throwable);
            logFailure(failed, throwable);
            return failed;
        }
    }

    public List<TradePackage> inbox() {
        return inbox.get();
    }

    public void removeFromInbox(String tradeId) {
        inbox.updateAndGet(current -> current.stream()
            .filter(trade -> !trade.tradeId().equals(tradeId))
            .toList());
    }

    public TradeStatus lastStatus() {
        return lastStatus.get();
    }

    private void completeRefresh(HttpResponse<String> response, Throwable throwable, Set<String> claimedTradeIds) {
        refreshInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            TradeStatus failed = TradeStatus.failure("Trade inbox request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            TradeStatus failed = TradeStatus.httpFailure("Server rejected trade inbox request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] trade inbox FAILED: " + failed.error());
            return;
        }

        try {
            List<TradePackage> parsed = parseInbox(response.body(), claimedTradeIds);
            inbox.set(parsed);
            TradeStatus status = TradeStatus.success("Trade inbox refreshed: " + parsed.size() + " pending.", statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] trade inbox SUCCESS: " + parsed.size() + " pending");
        } catch (Exception e) {
            TradeStatus failed = TradeStatus.failure("Could not parse trade inbox.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private void completeClaim(String tradeId, HttpResponse<String> response, Throwable throwable) {
        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            TradeStatus failed = TradeStatus.failure("Trade claim POST failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            lastStatus.set(TradeStatus.success("Trade claim posted: " + tradeId, statusCode));
            System.out.println("[Syx Duo Realm] trade claim SUCCESS: " + tradeId);
        } else {
            TradeStatus failed = TradeStatus.httpFailure("Server rejected trade claim: " + tradeId, statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] trade claim FAILED: " + failed.error());
        }
    }

    private List<TradePackage> parseInbox(String body, Set<String> claimedTradeIds) {
        Map<String, Object> json = JsonObjectParser.parse(body);
        Object trades = json.get("trades");
        if (!(trades instanceof List<?> list)) {
            return List.of();
        }

        List<TradePackage> out = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> trade)) {
                continue;
            }

            String tradeId = stringValue(trade.get("tradeId"));
            String resourceKey = stringValue(trade.get("resourceKey"));
            String fromPlayer = stringValue(trade.get("fromPlayer"));
            int amount = intValue(trade.get("amount"));

            if (tradeId.isBlank() || resourceKey.isBlank() || amount <= 0 || claimedTradeIds.contains(tradeId)) {
                continue;
            }
            out.add(new TradePackage(tradeId, resourceKey, amount, fromPlayer));
        }
        return List.copyOf(out);
    }

    private URI inboxUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        String query = "roomCode=" + encodeQuery(config.roomCode()) + "&playerName=" + encodeQuery(config.playerName());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/inbox", query, null);
    }

    private URI claimUri(SyxDuoRealmConfig config, String tradeId) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/trade/" + tradeId + "/claim", null, null);
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

    private String encodeQuery(String value) {
        return value
            .replace("%", "%25")
            .replace(" ", "%20")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace("?", "%3F");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void logFailure(TradeStatus status, Throwable throwable) {
        System.err.println("[Syx Duo Realm] trade FAILED: " + status.message());
        if (status.error() != null) {
            System.err.println("[Syx Duo Realm] trade error: " + status.error());
        }
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static final class TradeThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Syx Duo Realm Trade HTTP");
            thread.setDaemon(true);
            return thread;
        }
    }
}
