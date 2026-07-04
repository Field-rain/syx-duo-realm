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
import java.util.List;
import java.util.Map;

public final class SentTradeStore {

    public static final String STATUS_DEDUCTED = "DEDUCTED";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_REFUND_FAILED = "REFUND_FAILED";

    private final Path path;
    private final List<SentTradeRecord> records = new ArrayList<>();

    public SentTradeStore(Path path) {
        this.path = path;
        load();
    }

    public synchronized void addDeducted(
        String localTradeId,
        String roomCode,
        String fromPlayer,
        String toPlayer,
        String resourceKey,
        int amount,
        int availableAtSend
    ) throws IOException {
        String now = Instant.now().toString();
        records.add(new SentTradeRecord(
            localTradeId,
            "",
            STATUS_DEDUCTED,
            roomCode,
            fromPlayer,
            toPlayer,
            resourceKey,
            amount,
            availableAtSend,
            now,
            now,
            "Resources deducted locally before POST /api/trade."
        ));
        save();
    }

    public synchronized void mark(String localTradeId, String status, String serverTradeId, String note) throws IOException {
        String now = Instant.now().toString();
        for (int i = 0; i < records.size(); i++) {
            SentTradeRecord record = records.get(i);
            if (record.localTradeId().equals(localTradeId)) {
                records.set(i, record.withStatus(status, serverTradeId, now, note));
                save();
                return;
            }
        }
    }

    public synchronized int pendingDeductedCount() {
        int count = 0;
        for (SentTradeRecord record : records) {
            if (STATUS_DEDUCTED.equals(record.status())) {
                count++;
            }
        }
        return count;
    }

    public synchronized SentTradeRecord latest() {
        return records.isEmpty() ? null : records.get(records.size() - 1);
    }

    public synchronized List<SentTradeRecord> snapshot() {
        return List.copyOf(records);
    }

    public Path path() {
        return path;
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }

        try {
            Map<String, Object> json = JsonObjectParser.parse(Files.readString(path, StandardCharsets.UTF_8));
            Object values = json.get("sentTrades");
            if (values instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof Map<?, ?> map) {
                        records.add(fromMap(map));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Syx Duo Realm] Could not load sent trades from " + path);
            e.printStackTrace(System.err);
            records.clear();
        }
    }

    private SentTradeRecord fromMap(Map<?, ?> map) {
        return new SentTradeRecord(
            stringValue(map.get("localTradeId")),
            stringValue(map.get("serverTradeId")),
            stringValue(map.get("status")),
            stringValue(map.get("roomCode")),
            stringValue(map.get("fromPlayer")),
            stringValue(map.get("toPlayer")),
            stringValue(map.get("resourceKey")),
            intValue(map.get("amount")),
            intValue(map.get("availableAtSend")),
            stringValue(map.get("createdAt")),
            stringValue(map.get("updatedAt")),
            stringValue(map.get("note"))
        );
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("updatedAt", Instant.now().toString());
        json.put("sentTrades", records.stream().map(SentTradeRecord::toJsonMap).toList());

        Files.writeString(path, CityStateJsonWriter.writeMap(json), StandardCharsets.UTF_8);
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
}
