package com.syxduorealm.shadow;

import com.syxduorealm.config.JsonObjectParser;
import com.syxduorealm.export.CityStateJsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ShadowBindingStore {

    private final Path path;
    private ShadowBinding binding = ShadowBinding.none();

    public ShadowBindingStore(Path path) {
        this.path = path;
        load();
    }

    public synchronized ShadowBinding binding() {
        return binding;
    }

    public synchronized void save(ShadowBinding binding) throws IOException {
        this.binding = binding;
        Files.createDirectories(path.getParent());
        Files.writeString(path, CityStateJsonWriter.writeMap(binding.toJsonMap()), StandardCharsets.UTF_8);
    }

    public synchronized void clear() throws IOException {
        save(ShadowBinding.none());
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
            binding = new ShadowBinding(
                booleanValue(json.get("enabled")),
                intValue(json.get("friendFactionIndex"), -1),
                intValue(json.get("regionIndex"), -1),
                stringValue(json.get("friendPlayerName")),
                stringValue(json.get("friendSaveId")),
                stringValue(json.get("localSaveId")),
                stringValue(json.get("updatedAt"))
            );
        } catch (Exception e) {
            System.err.println("[Syx Duo Realm] Could not load shadow binding from " + path);
            e.printStackTrace(System.err);
            binding = ShadowBinding.none();
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
