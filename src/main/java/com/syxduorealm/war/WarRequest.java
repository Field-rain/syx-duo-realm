package com.syxduorealm.war;

public record WarRequest(
    String requestId,
    String roomCode,
    String fromPlayer,
    String toPlayer,
    String status,
    String createdAt,
    String updatedAt,
    String resolvedWarId,
    String note
) {
    public boolean pending() {
        return "PENDING".equals(status);
    }

    public boolean incoming(String playerName) {
        return pending() && playerName != null && playerName.equals(toPlayer);
    }

    public boolean outgoing(String playerName) {
        return pending() && playerName != null && playerName.equals(fromPlayer);
    }
}
