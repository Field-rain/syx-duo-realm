package com.syxduorealm.shadow;

public record ShadowFactionView(
    boolean active,
    int factionIndex,
    int regionIndex,
    String factionName,
    String regionName,
    String stanceName,
    String nativeStanceKey,
    String nativeStanceName,
    String mirroredServerRelation,
    String mirroredNativeStance,
    String mirroredAt,
    double localOffensivePower,
    double regionMilitaryPower,
    int regionPopulation
) {

    public static ShadowFactionView none() {
        return new ShadowFactionView(false, -1, -1, "", "", "", "", "", "", "", "", 0, 0, 0);
    }
}
