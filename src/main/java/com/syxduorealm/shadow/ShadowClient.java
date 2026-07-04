package com.syxduorealm.shadow;

import com.syxduorealm.config.JsonObjectParser;
import com.syxduorealm.config.SyxDuoRealmConfig;

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

public final class ShadowClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ShadowThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicReference<FriendState> latestFriendState = new AtomicReference<>();
    private final AtomicReference<ShadowStatus> lastStatus = new AtomicReference<>(ShadowStatus.notRun());

    public ShadowStatus refreshAsync(SyxDuoRealmConfig config) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            ShadowStatus status = ShadowStatus.inFlight("Friend state request already in progress.");
            lastStatus.set(status);
            return status;
        }

        ShadowStatus started = ShadowStatus.inFlight("Fetching friend_state.");
        lastStatus.set(started);
        latestFriendState.set(null);

        try {
            HttpRequest request = HttpRequest.newBuilder(friendStateUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeRefresh, executor);
        } catch (Throwable throwable) {
            refreshInFlight.set(false);
            ShadowStatus failed = ShadowStatus.failure("Could not start friend_state request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public FriendState latestFriendState() {
        return latestFriendState.get();
    }

    public ShadowStatus lastStatus() {
        return lastStatus.get();
    }

    private void completeRefresh(HttpResponse<String> response, Throwable throwable) {
        refreshInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            ShadowStatus failed = ShadowStatus.failure("Friend state request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            ShadowStatus failed = ShadowStatus.httpFailure("Server rejected friend_state request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] shadow FAILED: " + failed.error());
            return;
        }

        try {
            FriendState friendState = parseFriendState(response.body());
            latestFriendState.set(friendState);
            String message = friendState == null
                ? "No friend_state available. Sync another player into this room first."
                : "Friend state ready: " + friendState.playerName();
            ShadowStatus status = ShadowStatus.success(message, statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] shadow friend_state SUCCESS: " + message);
        } catch (Exception e) {
            latestFriendState.set(null);
            ShadowStatus failed = ShadowStatus.failure("Could not parse friend_state.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private FriendState parseFriendState(String body) {
        Map<String, Object> json = JsonObjectParser.parse(body);
        Object value = json.get("friend_state");
        if (!(value instanceof Map<?, ?> friend)) {
            return null;
        }

        return new FriendState(
            stringValue(friend.get("playerName")),
            stringValue(friend.get("saveId")),
            stringValue(friend.get("saveIdSource")),
            integerValue(friend.get("worldSeed")),
            stringValue(friend.get("playerFactionName")),
            integerValue(friend.get("populationTotal")),
            parsePopulationByRace(friend.get("populationByRace")),
            stringValue(friend.get("dominantRace")),
            parseResources(friend.get("resources")),
            integerValue(friend.get("activeNpcFactionsCount")),
            integerValue(friend.get("playerRealmRegionsCount")),
            parseMilitaryProfile(friend.get("militaryProfile")),
            stringValue(friend.get("receivedAt")),
            integerValue(friend.get("secondsSinceReceived")),
            booleanValue(friend.get("fresh")),
            integerValue(friend.get("staleAfterSeconds")),
            stringValue(friend.get("sentAt"))
        );
    }

    private Map<String, FriendState.RacePopulation> parsePopulationByRace(Object value) {
        Map<String, FriendState.RacePopulation> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> races)) {
            return out;
        }

        for (Map.Entry<?, ?> entry : races.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key.isBlank() || !(entry.getValue() instanceof Map<?, ?> race)) {
                continue;
            }
            out.put(key, new FriendState.RacePopulation(
                stringValue(race.get("name")),
                intValue(race.get("count"))
            ));
        }
        return Map.copyOf(out);
    }

    private Map<String, FriendState.ResourceAmount> parseResources(Object value) {
        Map<String, FriendState.ResourceAmount> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> resources)) {
            return out;
        }

        for (Map.Entry<?, ?> entry : resources.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key.isBlank() || !(entry.getValue() instanceof Map<?, ?> resource)) {
                continue;
            }
            out.put(key, new FriendState.ResourceAmount(
                stringValue(resource.get("name")),
                intValue(resource.get("amount"))
            ));
        }
        return Map.copyOf(out);
    }

    private FriendState.MilitaryProfile parseMilitaryProfile(Object value) {
        if (!(value instanceof Map<?, ?> profile)) {
            return null;
        }
        return new FriendState.MilitaryProfile(
            doubleValue(profile.get("offensivePower")),
            integerValue(profile.get("fieldArmyPower")),
            integerValue(profile.get("armyCount")),
            doubleValue(profile.get("capitalMilitaryPower")),
            integerValue(profile.get("realmRegions")),
            stringValue(profile.get("source"))
        );
    }

    private URI friendStateUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        String query = "roomCode=" + encodeQuery(config.roomCode()) + "&playerName=" + encodeQuery(config.playerName());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/friend_state", query, null);
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

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int intValue(Object value) {
        Integer parsed = integerValue(value);
        return parsed == null ? 0 : parsed;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(stringValue(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = stringValue(value);
        if (text.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void logFailure(ShadowStatus status, Throwable throwable) {
        System.err.println("[Syx Duo Realm] shadow FAILED: " + status.message());
        if (status.error() != null) {
            System.err.println("[Syx Duo Realm] shadow error: " + status.error());
        }
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static final class ShadowThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Syx Duo Realm Shadow HTTP");
            thread.setDaemon(true);
            return thread;
        }
    }
}
