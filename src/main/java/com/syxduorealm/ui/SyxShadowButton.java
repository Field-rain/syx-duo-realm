package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.shadow.FriendState;
import com.syxduorealm.shadow.ShadowBinding;
import com.syxduorealm.shadow.ShadowFactionView;
import com.syxduorealm.shadow.ShadowStatus;
import com.syxduorealm.war.DiplomacyRelation;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

public final class SyxShadowButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxShadowButton(SyxDuoRealmRuntime runtime) {
        super("SHD --");
        this.runtime = runtime;
        setDim(62, 24);
        clickActionSet(runtime::shadowPrimaryAction);
        hoverTitleSet("Syx Duo Realm Shadow");
    }

    @Override
    protected void renAction() {
        ShadowStatus status = runtime.shadowStatus();
        String labelText = labelText(status, runtime.shadowBindingActive(), runtime.shadowApplyPending(), runtime.latestFriendState());
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(runtime.shadowBindingActive());
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        ShadowStatus status = runtime.shadowStatus();
        ShadowBinding binding = runtime.shadowBinding();
        ShadowFactionView view = runtime.shadowFactionView();
        FriendState friend = runtime.latestFriendState();
        DiplomacyRelation relation = runtime.warRelationWithTarget();

        box.title("Syx Duo Realm Shadow");
        box.text("Controlled friend NPC shell");
        box.NL(6);
        box.text("Server: " + runtime.config().serverUrl());
        box.NL();
        box.text("Player: " + runtime.config().playerName());
        box.NL();
        box.text("Room: " + runtime.config().roomCode());
        box.NL();
        box.text("Default target: " + blankFallback(runtime.tradeOfferRecipient(), "(none)"));
        box.NL();
        box.text("Target source: " + runtime.friendTargetSource());
        box.NL(6);
        box.text("Native diplomacy mirror: " + runtime.config().mirrorNativeDiplomacy());
        box.NL();
        box.text("Peace maps to: " + runtime.config().nativePeaceStance());
        box.NL();
        if (relation != null) {
            box.text("Server relation: " + relation.status());
            box.NL();
            if (!relation.updatedAt().isBlank()) {
                box.text("Relation updated: " + relation.updatedAt());
                box.NL();
            }
        }
        box.NL(6);
        box.text("Status: " + status.state());
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
        box.text("Binding file: " + runtime.shadowBindingPath());
        box.NL();
        box.text("Binding active: " + binding.enabled());
        box.NL();
        if (binding.enabled()) {
            box.text("Friend: " + binding.friendPlayerName());
            box.NL();
            box.text("Faction index: " + binding.friendFactionIndex());
            box.NL();
            box.text("Region index: " + binding.regionIndex());
            box.NL();
            box.text("Friend save: " + binding.friendSaveId());
            box.NL();
            box.text("Local save: " + binding.localSaveId());
            box.NL();
        }
        if (view.active()) {
            box.NL(6);
            box.text("Local game NPC shell");
            box.NL();
            box.text("Faction: " + view.factionName() + " #" + view.factionIndex());
            box.NL();
            box.text("Region: " + view.regionName() + " #" + view.regionIndex());
            box.NL();
            box.text("Player stance: " + view.stanceName());
            box.NL();
            box.text("Native stance key: " + view.nativeStanceKey());
            box.NL();
            if (!view.mirroredServerRelation().isBlank()) {
                box.text("Mirrored: " + view.mirroredServerRelation() + " -> " + view.mirroredNativeStance());
                box.NL();
                box.text("Mirrored at: " + view.mirroredAt());
                box.NL();
            }
            box.text("Local power: " + (int) view.localOffensivePower());
            box.NL();
            box.text("Region military: " + (int) view.regionMilitaryPower());
            box.NL();
            box.text("Region population: " + view.regionPopulation());
            box.NL();
        }
        if (friend != null) {
            box.NL(6);
            box.text("Latest server friend_state");
            box.NL();
            box.text("Friend: " + friend.playerName());
            box.NL();
            box.text("Save: " + friend.saveId());
            box.NL();
            box.text("Population: " + friend.populationTotal());
            box.NL();
            box.text("Population races: " + friend.populationByRace().size());
            box.NL();
            box.text("Dominant race: " + blankFallback(friend.dominantRace(), "(unknown)"));
            box.NL();
            box.text("Resources: " + friend.resources().size());
            box.NL();
            box.text("Regions: " + friend.playerRealmRegionsCount());
            box.NL();
            if (friend.militaryProfile() != null && friend.militaryProfile().offensivePower() != null) {
                box.text("Server power: " + friend.militaryProfile().offensivePower().intValue());
                box.NL();
            }
            if (friend.fresh() != null) {
                box.text("Fresh: " + friend.fresh());
                if (friend.secondsSinceReceived() != null && friend.staleAfterSeconds() != null) {
                    box.text(" (" + friend.secondsSinceReceived() + "s/" + friend.staleAfterSeconds() + "s)");
                }
                box.NL();
            }
            box.text("Received: " + friend.receivedAt());
            box.NL();
        }
        box.NL(6);
        box.text(runtime.shadowBindingActive()
            ? "Click to remove the local NPC shell and clear binding. Active shells refresh from friend_state automatically."
            : "Click to fetch friend_state and create one controlled NPC shell.");
    }

    private String labelText(ShadowStatus status, boolean active, boolean pending, FriendState friend) {
        if (active) {
            if (friend != null && Boolean.FALSE.equals(friend.fresh())) {
                return "SHD OLD";
            }
            return "SHD ON";
        }
        if (pending || status.state() == ShadowStatus.State.IN_FLIGHT) {
            return "SHD ...";
        }
        if (status.state() == ShadowStatus.State.FAILED) {
            return "SHD ERR";
        }
        if (status.state() == ShadowStatus.State.SUCCESS) {
            return "SHD OK";
        }
        return "SHD --";
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
