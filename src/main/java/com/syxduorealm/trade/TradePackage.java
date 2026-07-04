package com.syxduorealm.trade;

public final class TradePackage {

    private final String tradeId;
    private final String resourceKey;
    private final int amount;
    private final String fromPlayer;

    public TradePackage(String tradeId, String resourceKey, int amount, String fromPlayer) {
        this.tradeId = tradeId;
        this.resourceKey = resourceKey;
        this.amount = amount;
        this.fromPlayer = fromPlayer;
    }

    public String tradeId() {
        return tradeId;
    }

    public String resourceKey() {
        return resourceKey;
    }

    public int amount() {
        return amount;
    }

    public String fromPlayer() {
        return fromPlayer;
    }
}
