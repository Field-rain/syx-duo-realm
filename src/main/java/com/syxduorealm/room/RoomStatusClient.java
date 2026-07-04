package com.syxduorealm.room;

import com.syxduorealm.config.JsonObjectParser;
import com.syxduorealm.config.SyxDuoRealmConfig;
import com.syxduorealm.shadow.FriendState;

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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RoomStatusClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new RoomThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicReference<RoomState> roomState = new AtomicReference<>();
    private final AtomicReference<RoomStatus> lastStatus = new AtomicReference<>(RoomStatus.notRun());

    public RoomStatus refreshAsync(SyxDuoRealmConfig config) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            RoomStatus status = RoomStatus.inFlight("Room status request already in progress.");
            lastStatus.set(status);
            return status;
        }

        RoomStatus started = RoomStatus.inFlight("Refreshing room status.");
        lastStatus.set(started);

        try {
            HttpRequest request = HttpRequest.newBuilder(roomStatusUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeRefresh, executor);
        } catch (Throwable throwable) {
            refreshInFlight.set(false);
            RoomStatus failed = RoomStatus.failure("Could not start room status request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public RoomState roomState(SyxDuoRealmConfig config) {
        RoomState state = roomState.get();
        return state == null ? RoomState.empty(config.roomCode(), config.playerName()) : state;
    }

    public RoomStatus lastStatus() {
        return lastStatus.get();
    }

    private void completeRefresh(HttpResponse<String> response, Throwable throwable) {
        refreshInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            RoomStatus failed = RoomStatus.failure("Room status request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            RoomStatus failed = RoomStatus.httpFailure("Server rejected room status request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] room status FAILED: " + failed.error());
            return;
        }

        try {
            RoomState parsed = parseRoomState(response.body());
            roomState.set(parsed);
            RoomStatus status = RoomStatus.success(
                "Room " + parsed.roomCode() + ": " + parsed.freshPlayerCount() + "/" + parsed.playerCount() + " fresh players.",
                statusCode
            );
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] room status SUCCESS: " + status.message());
        } catch (Exception e) {
            RoomStatus failed = RoomStatus.failure("Could not parse room status.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private RoomState parseRoomState(String body) {
        Map<String, Object> json = JsonObjectParser.parse(body);
        FriendState friendState = parseFriendState(json.get("friend_state"));

        return new RoomState(
            stringValue(json.get("serverTime")),
            stringValue(json.get("roomCode")),
            stringValue(json.get("playerName")),
            intValue(json.get("staleAfterSeconds")),
            booleanValue(json.get("exists")),
            booleanValue(json.get("ready")),
            booleanValue(json.get("freshReady")),
            booleanValue(json.get("friendPresent")),
            booleanValue(json.get("friendFresh")),
            intValue(json.get("playerCount")),
            intValue(json.get("freshPlayerCount")),
            parsePlayers(json.get("players")),
            friendState
        );
    }

    private List<RoomPlayerState> parsePlayers(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<RoomPlayerState> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> player)) {
                continue;
            }
            out.add(new RoomPlayerState(
                stringValue(player.get("playerKey")),
                stringValue(player.get("playerName")),
                stringValue(player.get("saveId")),
                stringValue(player.get("saveIdSource")),
                stringValue(player.get("receivedAt")),
                stringValue(player.get("sentAt")),
                integerValue(player.get("secondsSinceReceived")),
                booleanValue(player.get("fresh")),
                integerValue(player.get("worldSeed")),
                integerValue(player.get("protocolVersion")),
                stringValue(player.get("modVersion")),
                stringValue(player.get("gameVersion")),
                stringValue(player.get("playerFactionName")),
                integerValue(player.get("populationTotal")),
                integerValue(player.get("activeNpcFactionsCount")),
                integerValue(player.get("playerRealmRegionsCount"))
            ));
        }
        return List.copyOf(out);
    }

    private FriendState parseFriendState(Object value) {
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
        return out;
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
        return out;
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

    private URI roomStatusUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        String query = "roomCode=" + encodeQuery(config.roomCode()) + "&playerName=" + encodeQuery(config.playerName());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/room_status", query, null);
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

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private int intValue(Object value) {
        Integer parsed = integerValue(value);
        return parsed == null ? 0 : parsed;
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

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void logFailure(RoomStatus status, Throwable throwable) {
        System.err.println("[Syx Duo Realm] room status FAILED: " + status.message());
        if (status.error() != null) {
            System.err.println("[Syx Duo Realm] room status error: " + status.error());
        }
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static final class RoomThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Syx Duo Realm Room HTTP");
            thread.setDaemon(true);
            return thread;
        }
    }
}
