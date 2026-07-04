package com.syxduorealm.shadow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ShadowBinding(
    boolean enabled,
    int friendFactionIndex,
    int regionIndex,
    String friendPlayerName,
    String friendSaveId,
    String localSaveId,
    String updatedAt
) {

    public static ShadowBinding none() {
        return new ShadowBinding(false, -1, -1, "", "", "", null);
    }

    public static ShadowBinding active(
        int friendFactionIndex,
        int regionIndex,
        FriendState friendState,
        String localSaveId
    ) {
        return new ShadowBinding(
            true,
            friendFactionIndex,
            regionIndex,
            text(friendState.playerName()),
            text(friendState.saveId()),
            text(localSaveId),
            Instant.now().toString()
        );
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("friendFactionIndex", friendFactionIndex);
        out.put("regionIndex", regionIndex);
        out.put("friendPlayerName", friendPlayerName);
        out.put("friendSaveId", friendSaveId);
        out.put("localSaveId", localSaveId);
        out.put("updatedAt", updatedAt);
        return out;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
