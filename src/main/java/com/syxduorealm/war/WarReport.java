package com.syxduorealm.war;

public record WarReport(
    String warId,
    String roomCode,
    String attacker,
    String defender,
    String winner,
    String loser,
    int attackerScore,
    int defenderScore,
    int attackerRoll,
    int defenderRoll,
    int attackerEstimatedLosses,
    int defenderEstimatedLosses,
    int margin,
    String summary,
    String createdAt
) {
    public boolean involves(String playerName) {
        return playerName != null && (playerName.equals(attacker) || playerName.equals(defender));
    }

    public boolean playerWon(String playerName) {
        return playerName != null && playerName.equals(winner);
    }
}
