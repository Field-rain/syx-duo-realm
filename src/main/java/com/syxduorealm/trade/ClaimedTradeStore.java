package com.syxduorealm.trade;

import com.syxduorealm.config.JsonObjectParser;
import com.syxduorealm.export.CityStateJsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClaimedTradeStore {

    private final Path path;
    private final LinkedHashSet<String> claimedTradeIds = new LinkedHashSet<>();

    public ClaimedTradeStore(Path path) {
        this.path = path;
        load();
    }

    public synchronized boolean contains(String tradeId) {
        return claimedTradeIds.contains(tradeId);
    }

    public synchronized boolean markClaimed(String tradeId) throws IOException {
        if (!claimedTradeIds.add(tradeId)) {
            return false;
        }
        save();
        return true;
    }

    public synchronized Set<String> snapshot() {
        return Set.copyOf(claimedTradeIds);
    }

    public Path path() {
        return path;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(path)) {
            return;
        }

        try {
            Map<String, Object> json = JsonObjectParser.parse(Files.readString(path, StandardCharsets.UTF_8));
            Object values = json.get("claimedTradeIds");
            if (values instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof String tradeId && !tradeId.isBlank()) {
                        claimedTradeIds.add(tradeId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Syx Duo Realm] Could not load claimed trades from " + path);
            e.printStackTrace(System.err);
            claimedTradeIds.clear();
        }
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("updatedAt", Instant.now().toString());
        json.put("claimedTradeIds", new ArrayList<>(claimedTradeIds));

        Files.writeString(path, CityStateJsonWriter.writeMap(json), StandardCharsets.UTF_8);
    }
}
