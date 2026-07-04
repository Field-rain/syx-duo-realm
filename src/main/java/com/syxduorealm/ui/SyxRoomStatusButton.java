package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.room.RoomPlayerState;
import com.syxduorealm.room.RoomState;
import com.syxduorealm.room.RoomStatus;
import com.syxduorealm.shadow.FriendState;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

public final class SyxRoomStatusButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxRoomStatusButton(SyxDuoRealmRuntime runtime) {
        super("R --");
        this.runtime = runtime;
        setDim(58, 24);
        clickActionSet(runtime::roomPrimaryAction);
        hoverTitleSet("Syx Duo Realm Room");
    }

    @Override
    protected void renAction() {
        RoomStatus status = runtime.lastRoomStatus();
        RoomState state = runtime.roomState();
        String labelText = labelText(status, state);
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(state.freshReady());
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        RoomStatus status = runtime.lastRoomStatus();
        RoomState state = runtime.roomState();

        box.title("Syx Duo Realm Room");
        box.text("Friend LAN readiness");
        box.NL(6);
        box.text("Server: " + runtime.config().serverUrl());
        box.NL();
        box.text("Player: " + runtime.config().playerName());
        box.NL();
        box.text("Room: " + runtime.config().roomCode());
        box.NL();
        box.text("Fresh window: " + state.staleAfterSeconds() + " seconds");
        box.NL(6);
        box.text("Request: " + status.state());
        box.NL();
        box.text(status.message());
        box.NL();
        if (status.timestamp() != null) {
            box.text("Time: " + status.timestamp());
            box.NL();
        }
        if (status.statusCode() != null) {
            box.text("HTTP: " + status.statusCode());
            box.NL();
        }
        if (status.error() != null) {
            box.text("Error: " + status.error());
            box.NL();
        }
        box.NL(6);
        box.text("Room exists: " + state.exists());
        box.NL();
        box.text("Ready: " + state.ready());
        box.NL();
        box.text("Fresh ready: " + state.freshReady());
        box.NL();
        box.text("Friend present: " + state.friendPresent());
        box.NL();
        box.text("Friend fresh: " + state.friendFresh());
        box.NL();
        box.text("Players: " + state.freshPlayerCount() + " fresh / " + state.playerCount() + " total");
        box.NL();
        if (state.serverTime() != null && !state.serverTime().isBlank()) {
            box.text("Server time: " + state.serverTime());
            box.NL();
        }
        box.NL(6);
        for (RoomPlayerState player : state.players()) {
            box.text((player.fresh() ? "* " : "- ") + player.playerName()
                + " pop=" + valueText(player.populationTotal())
                + " save=" + blankFallback(player.saveId(), "?")
                + " age=" + secondsText(player.secondsSinceReceived()));
            box.NL();
            box.text("  " + blankFallback(player.gameVersion(), "?") + " mod=" + blankFallback(player.modVersion(), "?")
                + " protocol=" + valueText(player.protocolVersion()));
            box.NL();
        }
        FriendState friendState = state.friendState();
        if (friendState != null) {
            box.NL(6);
            box.text("Friend state: " + friendState.playerName());
            box.NL();
            box.text("Friend save: " + friendState.saveId());
            box.NL();
            box.text("Friend population: " + friendState.populationTotal());
            box.NL();
            box.text("Friend regions: " + friendState.playerRealmRegionsCount());
            box.NL();
        }
        box.NL(6);
        box.text("Click to refresh room status now.");
    }

    private String labelText(RoomStatus status, RoomState state) {
        if (status.state() == RoomStatus.State.FAILED) {
            return "R ERR";
        }
        if (status.state() == RoomStatus.State.IN_FLIGHT) {
            return "R ...";
        }
        if (state.freshReady()) {
            return "R OK";
        }
        if (state.friendPresent() && !state.friendFresh()) {
            return "R OLD";
        }
        if (state.playerCount() <= 0) {
            return "R 0/2";
        }
        return "R " + state.freshPlayerCount() + "/" + Math.max(2, state.playerCount());
    }

    private String secondsText(Integer seconds) {
        return seconds == null ? "?" : seconds + "s";
    }

    private String valueText(Integer value) {
        return value == null ? "?" : String.valueOf(value);
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
