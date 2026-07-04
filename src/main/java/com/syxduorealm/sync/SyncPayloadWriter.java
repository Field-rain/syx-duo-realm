package com.syxduorealm.sync;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.config.SyxDuoRealmConfig;
import com.syxduorealm.export.CityState;
import com.syxduorealm.export.CityStateJsonWriter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class SyncPayloadWriter {

    private static final int PROTOCOL_VERSION = 1;
    private static final String MOD_VERSION = "0.3.1";
    private static final String GAME_VERSION = "V71";

    private SyncPayloadWriter() {
    }

    static String write(SyxDuoRealmConfig config, CityState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modName", SyxDuoRealmRuntime.MOD_NAME);
        out.put("protocolVersion", PROTOCOL_VERSION);
        out.put("modVersion", MOD_VERSION);
        out.put("gameVersion", GAME_VERSION);
        out.put("playerName", config.playerName());
        out.put("roomCode", config.roomCode());
        out.put("sentAt", Instant.now().toString());
        out.put("cityState", state.toJsonMap());
        return CityStateJsonWriter.writeMap(out);
    }
}
