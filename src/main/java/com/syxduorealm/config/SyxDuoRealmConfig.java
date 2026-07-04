package com.syxduorealm.config;

import com.syxduorealm.export.CityStateJsonWriter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SyxDuoRealmConfig {

    public static final String DEFAULT_SERVER_URL = "http://localhost:8787/api/state";
    public static final String DEFAULT_ROOM_CODE = "local";
    public static final int DEFAULT_SYNC_INTERVAL_SECONDS = 30;
    public static final String DEFAULT_TRADE_OFFER_RESOURCE_KEY = "FISH";
    public static final int DEFAULT_TRADE_OFFER_AMOUNT = 25;
    public static final String DEFAULT_TRADE_OFFER_TO_PLAYER = "";
    public static final boolean DEFAULT_MIRROR_NATIVE_DIPLOMACY = true;
    public static final String DEFAULT_NATIVE_PEACE_STANCE = "TRADE";
    private static final int MIN_SYNC_INTERVAL_SECONDS = 5;
    private static final int MAX_SYNC_INTERVAL_SECONDS = 3600;
    private static final int MIN_TRADE_OFFER_AMOUNT = 1;
    private static final int MAX_TRADE_OFFER_AMOUNT = 100000;

    private final Path path;
    private final String serverUrl;
    private final String playerName;
    private final String roomCode;
    private final int syncIntervalSeconds;
    private String tradeOfferResourceKey;
    private int tradeOfferAmount;
    private String tradeOfferToPlayer;
    private final boolean mirrorNativeDiplomacy;
    private final String nativePeaceStance;

    private SyxDuoRealmConfig(
        Path path,
        String serverUrl,
        String playerName,
        String roomCode,
        int syncIntervalSeconds,
        String tradeOfferResourceKey,
        int tradeOfferAmount,
        String tradeOfferToPlayer,
        boolean mirrorNativeDiplomacy,
        String nativePeaceStance
    ) {
        this.path = path;
        this.serverUrl = serverUrl;
        this.playerName = playerName;
        this.roomCode = roomCode;
        this.syncIntervalSeconds = syncIntervalSeconds;
        this.tradeOfferResourceKey = tradeOfferResourceKey;
        this.tradeOfferAmount = tradeOfferAmount;
        this.tradeOfferToPlayer = tradeOfferToPlayer;
        this.mirrorNativeDiplomacy = mirrorNativeDiplomacy;
        this.nativePeaceStance = nativePeaceStance;
    }

    public static LoadResult loadOrCreate(Path path) {
        SyxDuoRealmConfig defaults = defaults(path);

        if (!Files.exists(path)) {
            try {
                defaults.save();
                return LoadResult.created(defaults, "Created default config.");
            } catch (IOException e) {
                return LoadResult.failed(defaults, "Could not create config; using defaults.", e);
            }
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, Object> values = JsonObjectParser.parse(json);
            SyxDuoRealmConfig config = fromMap(path, values, defaults);
            if (hasMissingOptionalKeys(values)) {
                config.save();
            }
            return LoadResult.loaded(config, "Loaded config.");
        } catch (Exception e) {
            return LoadResult.failed(defaults, "Could not read config; using defaults.", e);
        }
    }

    public void save() throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, CityStateJsonWriter.writeMap(toJsonMap()), StandardCharsets.UTF_8);
    }

    public synchronized void updateTradeOfferResourceKey(String tradeOfferResourceKey) throws IOException {
        this.tradeOfferResourceKey = blankToFallback(tradeOfferResourceKey, DEFAULT_TRADE_OFFER_RESOURCE_KEY);
        save();
    }

    public synchronized void updateTradeOfferAmount(int tradeOfferAmount) throws IOException {
        this.tradeOfferAmount = clamp(tradeOfferAmount, MIN_TRADE_OFFER_AMOUNT, MAX_TRADE_OFFER_AMOUNT);
        save();
    }

    public Path path() {
        return path;
    }

    public String serverUrl() {
        return serverUrl;
    }

    public String playerName() {
        return playerName;
    }

    public String roomCode() {
        return roomCode;
    }

    public int syncIntervalSeconds() {
        return syncIntervalSeconds;
    }

    public long syncIntervalMillis() {
        return syncIntervalSeconds * 1000L;
    }

    public String tradeOfferResourceKey() {
        return tradeOfferResourceKey;
    }

    public int tradeOfferAmount() {
        return tradeOfferAmount;
    }

    public String tradeOfferToPlayer() {
        return tradeOfferToPlayer;
    }

    public boolean mirrorNativeDiplomacy() {
        return mirrorNativeDiplomacy;
    }

    public String nativePeaceStance() {
        return nativePeaceStance;
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverUrl", serverUrl);
        out.put("playerName", playerName);
        out.put("roomCode", roomCode);
        out.put("syncIntervalSeconds", syncIntervalSeconds);
        out.put("tradeOfferResourceKey", tradeOfferResourceKey);
        out.put("tradeOfferAmount", tradeOfferAmount);
        out.put("tradeOfferToPlayer", tradeOfferToPlayer);
        out.put("mirrorNativeDiplomacy", mirrorNativeDiplomacy);
        out.put("nativePeaceStance", nativePeaceStance);
        return out;
    }

    private static SyxDuoRealmConfig defaults(Path path) {
        return new SyxDuoRealmConfig(
            path,
            DEFAULT_SERVER_URL,
            defaultPlayerName(),
            DEFAULT_ROOM_CODE,
            DEFAULT_SYNC_INTERVAL_SECONDS,
            DEFAULT_TRADE_OFFER_RESOURCE_KEY,
            DEFAULT_TRADE_OFFER_AMOUNT,
            DEFAULT_TRADE_OFFER_TO_PLAYER,
            DEFAULT_MIRROR_NATIVE_DIPLOMACY,
            DEFAULT_NATIVE_PEACE_STANCE
        );
    }

    private static SyxDuoRealmConfig fromMap(Path path, Map<String, Object> values, SyxDuoRealmConfig fallback) {
        String serverUrl = stringValue(values.get("serverUrl"), fallback.serverUrl);
        String playerName = stringValue(values.get("playerName"), fallback.playerName);
        String roomCode = stringValue(values.get("roomCode"), fallback.roomCode);
        int syncIntervalSeconds = intValue(values.get("syncIntervalSeconds"), fallback.syncIntervalSeconds);
        String tradeOfferResourceKey = stringValue(values.get("tradeOfferResourceKey"), fallback.tradeOfferResourceKey);
        int tradeOfferAmount = intValue(values.get("tradeOfferAmount"), fallback.tradeOfferAmount);
        String tradeOfferToPlayer = stringValue(values.get("tradeOfferToPlayer"), fallback.tradeOfferToPlayer);
        boolean mirrorNativeDiplomacy = booleanValue(values.get("mirrorNativeDiplomacy"), fallback.mirrorNativeDiplomacy);
        String nativePeaceStance = stringValue(values.get("nativePeaceStance"), fallback.nativePeaceStance);

        serverUrl = normalizeServerUrl(serverUrl, fallback.serverUrl);
        playerName = blankToFallback(playerName, fallback.playerName);
        roomCode = blankToFallback(roomCode, fallback.roomCode);
        syncIntervalSeconds = clamp(syncIntervalSeconds, MIN_SYNC_INTERVAL_SECONDS, MAX_SYNC_INTERVAL_SECONDS);
        tradeOfferResourceKey = blankToFallback(tradeOfferResourceKey, fallback.tradeOfferResourceKey);
        tradeOfferAmount = clamp(tradeOfferAmount, MIN_TRADE_OFFER_AMOUNT, MAX_TRADE_OFFER_AMOUNT);
        tradeOfferToPlayer = trimToBlank(tradeOfferToPlayer);
        nativePeaceStance = normalizeNativePeaceStance(nativePeaceStance, fallback.nativePeaceStance);

        return new SyxDuoRealmConfig(
            path,
            serverUrl,
            playerName,
            roomCode,
            syncIntervalSeconds,
            tradeOfferResourceKey,
            tradeOfferAmount,
            tradeOfferToPlayer,
            mirrorNativeDiplomacy,
            nativePeaceStance
        );
    }

    private static String normalizeServerUrl(String value, String fallback) {
        String trimmed = blankToFallback(value, fallback);
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return fallback;
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static String normalizeNativePeaceStance(String value, String fallback) {
        String stance = blankToFallback(value, fallback).toUpperCase();
        return switch (stance) {
            case "NEUTRAL", "TRADE", "PACT", "ALLY" -> stance;
            default -> fallback;
        };
    }

    private static String blankToFallback(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String trimToBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String defaultPlayerName() {
        return blankToFallback(System.getProperty("user.name"), "Player");
    }

    private static boolean hasMissingOptionalKeys(Map<String, Object> values) {
        return !values.containsKey("tradeOfferResourceKey")
            || !values.containsKey("tradeOfferAmount")
            || !values.containsKey("tradeOfferToPlayer")
            || !values.containsKey("mirrorNativeDiplomacy")
            || !values.containsKey("nativePeaceStance");
    }

    public static final class LoadResult {
        private final SyxDuoRealmConfig config;
        private final boolean created;
        private final String message;
        private final Throwable error;

        private LoadResult(SyxDuoRealmConfig config, boolean created, String message, Throwable error) {
            this.config = config;
            this.created = created;
            this.message = message;
            this.error = error;
        }

        private static LoadResult loaded(SyxDuoRealmConfig config, String message) {
            return new LoadResult(config, false, message, null);
        }

        private static LoadResult created(SyxDuoRealmConfig config, String message) {
            return new LoadResult(config, true, message, null);
        }

        private static LoadResult failed(SyxDuoRealmConfig config, String message, Throwable error) {
            return new LoadResult(config, false, message, error);
        }

        public SyxDuoRealmConfig config() {
            return config;
        }

        public boolean created() {
            return created;
        }

        public String message() {
            return message;
        }

        public Throwable error() {
            return error;
        }
    }
}
