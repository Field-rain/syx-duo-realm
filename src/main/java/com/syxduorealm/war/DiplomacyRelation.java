package com.syxduorealm.war;

import java.util.List;

public record DiplomacyRelation(
    String relationId,
    String roomCode,
    String playerA,
    String playerB,
    String status,
    String source,
    String createdAt,
    String updatedAt,
    String lastWarId
) {

    public boolean involves(String playerName) {
        return playerName != null && (playerName.equals(playerA) || playerName.equals(playerB));
    }

    public boolean involvesPair(String left, String right) {
        return involves(left) && involves(right);
    }

    public boolean atWar() {
        return "WAR".equals(status);
    }

    public String other(String playerName) {
        if (playerName == null) {
            return "";
        }
        if (playerName.equals(playerA)) {
            return playerB;
        }
        if (playerName.equals(playerB)) {
            return playerA;
        }
        return "";
    }

    public static DiplomacyRelation peace(String roomCode, String playerName, String friendName) {
        List<String> players = sorted(playerName, friendName);
        String relationId = safe(roomCode, "local") + "::" + players.get(0) + "::" + players.get(1);
        return new DiplomacyRelation(
            relationId,
            safe(roomCode, "local"),
            players.get(0),
            players.get(1),
            "PEACE",
            "implicit",
            "",
            "",
            ""
        );
    }

    public static DiplomacyRelation warFromReport(WarReport report) {
        List<String> players = sorted(report.attacker(), report.defender());
        String relationId = safe(report.roomCode(), "local") + "::" + players.get(0) + "::" + players.get(1);
        return new DiplomacyRelation(
            relationId,
            safe(report.roomCode(), "local"),
            players.get(0),
            players.get(1),
            "WAR",
            "war-report",
            report.createdAt(),
            report.createdAt(),
            report.warId()
        );
    }

    private static List<String> sorted(String left, String right) {
        String a = safe(left, "player");
        String b = safe(right, "friend");
        return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
    }

    private static String safe(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? fallback : text;
    }
}
