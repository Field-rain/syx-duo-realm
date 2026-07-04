package com.syxduorealm.room;

import com.syxduorealm.shadow.FriendState;

import java.util.List;

public record RoomState(
    String serverTime,
    String roomCode,
    String playerName,
    int staleAfterSeconds,
    boolean exists,
    boolean ready,
    boolean freshReady,
    boolean friendPresent,
    boolean friendFresh,
    int playerCount,
    int freshPlayerCount,
    List<RoomPlayerState> players,
    FriendState friendState
) {
    public static RoomState empty(String roomCode, String playerName) {
        return new RoomState(
            null,
            roomCode,
            playerName,
            0,
            false,
            false,
            false,
            false,
            false,
            0,
            0,
            List.of(),
            null
        );
    }
}
