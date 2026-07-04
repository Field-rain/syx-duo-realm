package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.war.DiplomacyRelation;
import com.syxduorealm.war.PeaceRequest;
import com.syxduorealm.war.WarStatus;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

import java.util.List;

public final class SyxPeaceButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxPeaceButton(SyxDuoRealmRuntime runtime) {
        super("PCE");
        this.runtime = runtime;
        setDim(64, 24);
        clickActionSet(runtime::peacePrimaryAction);
        hoverTitleSet("Syx Duo Realm Peace");
    }

    @Override
    protected void renAction() {
        WarStatus status = runtime.warStatus();
        PeaceRequest incoming = runtime.incomingPeaceRequest();
        PeaceRequest outgoing = runtime.outgoingPeaceRequest();
        DiplomacyRelation relation = runtime.warRelationWithTarget();
        String labelText = labelText(status, incoming, outgoing, relation);
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(incoming != null || relation != null && !relation.atWar());
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        WarStatus status = runtime.warStatus();
        List<PeaceRequest> requests = runtime.peaceRequests();
        PeaceRequest incoming = runtime.incomingPeaceRequest();
        PeaceRequest outgoing = runtime.outgoingPeaceRequest();
        DiplomacyRelation relation = runtime.warRelationWithTarget();

        box.title("Syx Duo Realm Peace");
        box.text("Server-side peace request");
        box.NL(6);
        box.text("Target: " + blankFallback(runtime.tradeOfferRecipient(), "(none)"));
        box.NL();
        box.text("Target source: " + runtime.friendTargetSource());
        box.NL();
        box.text("This changes only server diplomacy state.");
        box.NL();
        box.text("It does not write original game DIP.WAR().");
        box.NL(6);

        if (relation != null) {
            box.text("Server relation: " + relation.status());
            box.NL();
            if (!relation.updatedAt().isBlank()) {
                box.text("Relation updated: " + relation.updatedAt());
                box.NL();
            }
            if (!relation.lastWarId().isBlank()) {
                box.text("Last war: " + relation.lastWarId());
                box.NL();
            }
            box.NL(6);
        }

        if (incoming != null) {
            box.text("Incoming peace request from " + incoming.fromPlayer());
            box.NL();
            box.text("Click PCE to accept.");
            box.NL(6);
        } else if (outgoing != null) {
            box.text("Outgoing peace request to " + outgoing.toPlayer());
            box.NL();
            box.text("Waiting for the other player to accept.");
            box.NL(6);
        }

        box.text("Status: " + status.state());
        box.NL();
        box.text(status.message());
        box.NL();
        if (status.error() != null) {
            box.text("Error: " + status.error());
            box.NL();
        }

        box.NL(6);
        box.text("Peace requests: " + requests.size());
        box.NL();
        for (int i = 0; i < requests.size() && i < 4; i++) {
            PeaceRequest request = requests.get(i);
            box.text((i + 1) + ". " + request.status() + " " + request.fromPlayer() + " -> " + request.toPlayer());
            box.NL();
        }
        if (requests.size() > 4) {
            box.text("... " + (requests.size() - 4) + " more");
            box.NL();
        }

        box.NL(6);
        box.text("Click to accept an incoming peace request or send one during WAR.");
    }

    private String labelText(
        WarStatus status,
        PeaceRequest incoming,
        PeaceRequest outgoing,
        DiplomacyRelation relation
    ) {
        if (status.state() == WarStatus.State.IN_FLIGHT) {
            return "PCE ...";
        }
        if (status.state() == WarStatus.State.FAILED) {
            return "PCE ERR";
        }
        if (incoming != null) {
            return "PCE IN";
        }
        if (outgoing != null) {
            return "PCE OUT";
        }
        if (relation != null && relation.atWar()) {
            return "PCE";
        }
        return "PCE OK";
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
