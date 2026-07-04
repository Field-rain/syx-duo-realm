package com.syxduorealm.room;

public record RoomPlayerState(
    String playerKey,
    String playerName,
    String saveId,
    String saveIdSource,
    String receivedAt,
    String sentAt,
    Integer secondsSinceReceived,
    boolean fresh,
    Integer worldSeed,
    Integer protocolVersion,
    String modVersion,
    String gameVersion,
    String playerFactionName,
    Integer populationTotal,
    Integer activeNpcFactionsCount,
    Integer playerRealmRegionsCount
) {
}
