package com.syxduorealm.export;

public final class SaveIdentity {

    public static final String UNSAVED = "unsaved";

    private final String id;
    private final String source;

    private SaveIdentity(String id, String source) {
        this.id = id;
        this.source = source;
    }

    public static SaveIdentity resolve(String trackedSaveId, String playerName, String roomCode, Integer worldSeed) {
        String tracked = cleanTrackedSaveId(trackedSaveId);
        if (!UNSAVED.equals(tracked)) {
            return new SaveIdentity(tracked, "savePath");
        }

        String room = slug(roomCode, "room");
        String player = slug(playerName, "player");
        if (worldSeed != null) {
            return new SaveIdentity(room + "_" + player + "_seed_" + worldSeed, "fallbackWorldSeed");
        }
        return new SaveIdentity(room + "_" + player, "fallbackConfig");
    }

    public String id() {
        return id;
    }

    public String source() {
        return source;
    }

    private static String cleanTrackedSaveId(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || UNSAVED.equalsIgnoreCase(cleaned)) {
            return UNSAVED;
        }
        return cleaned;
    }

    private static String slug(String value, String fallback) {
        String source = value == null ? "" : value.trim();
        StringBuilder out = new StringBuilder(source.length());
        boolean lastWasSeparator = false;

        for (int i = 0; i < source.length(); i++) {
            char ch = Character.toLowerCase(source.charAt(i));
            boolean accepted = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
            if (accepted) {
                out.append(ch);
                lastWasSeparator = false;
            } else if (!lastWasSeparator && out.length() > 0) {
                out.append('_');
                lastWasSeparator = true;
            }
        }

        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
            out.deleteCharAt(out.length() - 1);
        }

        if (out.isEmpty()) {
            return fallback;
        }
        if (out.length() > 48) {
            return out.substring(0, 48);
        }
        return out.toString();
    }
}
