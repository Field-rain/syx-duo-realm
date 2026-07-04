package com.syxduorealm.trade;

import java.util.LinkedHashMap;
import java.util.Map;

public record SentTradeRecord(
    String localTradeId,
    String serverTradeId,
    String status,
    String roomCode,
    String fromPlayer,
    String toPlayer,
    String resourceKey,
    int amount,
    int availableAtSend,
    String createdAt,
    String updatedAt,
    String note
) {

    public SentTradeRecord withStatus(String status, String serverTradeId, String updatedAt, String note) {
        return new SentTradeRecord(
            localTradeId,
            serverTradeId == null || serverTradeId.isBlank() ? this.serverTradeId : serverTradeId,
            status,
            roomCode,
            fromPlayer,
            toPlayer,
            resourceKey,
            amount,
            availableAtSend,
            createdAt,
            updatedAt,
            note == null ? "" : note
        );
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("localTradeId", localTradeId);
        out.put("serverTradeId", serverTradeId);
        out.put("status", status);
        out.put("roomCode", roomCode);
        out.put("fromPlayer", fromPlayer);
        out.put("toPlayer", toPlayer);
        out.put("resourceKey", resourceKey);
        out.put("amount", amount);
        out.put("availableAtSend", availableAtSend);
        out.put("createdAt", createdAt);
        out.put("updatedAt", updatedAt);
        out.put("note", note);
        return out;
    }
}
