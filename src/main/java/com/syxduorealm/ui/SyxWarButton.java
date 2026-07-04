package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.war.DiplomacyRelation;
import com.syxduorealm.war.WarReport;
import com.syxduorealm.war.WarRequest;
import com.syxduorealm.war.WarStatus;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

import java.util.List;

public final class SyxWarButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxWarButton(SyxDuoRealmRuntime runtime) {
        super("WAR");
        this.runtime = runtime;
        setDim(64, 24);
        clickActionSet(runtime::warPrimaryAction);
        hoverTitleSet("Syx Duo Realm War");
    }

    @Override
    protected void renAction() {
        WarStatus status = runtime.warStatus();
        WarRequest incoming = runtime.incomingWarRequest();
        WarRequest outgoing = runtime.outgoingWarRequest();
        DiplomacyRelation relation = runtime.warRelationWithTarget();
        String labelText = labelText(status, runtime.latestWarReport(), incoming, outgoing, relation);
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        WarReport latest = runtime.latestWarReport();
        selectedSet(incoming != null || relation != null && relation.atWar() || latest != null && latest.playerWon(runtime.config().playerName()));
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        WarStatus status = runtime.warStatus();
        List<WarReport> reports = runtime.warReports();
        List<WarRequest> requests = runtime.warRequests();
        List<DiplomacyRelation> relations = runtime.warRelations();
        WarRequest incoming = runtime.incomingWarRequest();
        WarRequest outgoing = runtime.outgoingWarRequest();
        DiplomacyRelation targetRelation = runtime.warRelationWithTarget();

        box.title("Syx Duo Realm War");
        box.text("Async war request and server-side auto report");
        box.NL(6);
        box.text("Target: " + blankFallback(runtime.tradeOfferRecipient(), "(none)"));
        box.NL();
        box.text("Target source: " + runtime.friendTargetSource());
        box.NL();
        box.text("No resources, armies, regions, or saves are modified in this phase.");
        box.NL(6);
        if (targetRelation != null) {
            box.text("Server relation: " + targetRelation.status());
            box.NL();
            if (!targetRelation.updatedAt().isBlank()) {
                box.text("Relation updated: " + targetRelation.updatedAt());
                box.NL();
            }
            if (!targetRelation.lastWarId().isBlank()) {
                box.text("Last relation war: " + targetRelation.lastWarId());
                box.NL();
            }
            if (targetRelation.atWar()) {
                box.text("Click WAR to resolve another controlled battle report.");
                box.NL();
            }
            box.NL(6);
        }
        if (incoming != null) {
            box.text("Incoming request from " + incoming.fromPlayer());
            box.NL();
            box.text("Click WAR to accept and resolve.");
            box.NL(6);
        } else if (outgoing != null) {
            box.text("Outgoing request to " + outgoing.toPlayer());
            box.NL();
            box.text("Waiting for the other player to accept.");
            box.NL(6);
        }
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
        box.text("Relations: " + relations.size());
        box.NL();
        for (int i = 0; i < relations.size() && i < 4; i++) {
            DiplomacyRelation relation = relations.get(i);
            box.text((i + 1) + ". " + relation.status() + " " + relation.playerA() + " <-> " + relation.playerB());
            box.NL();
        }
        if (relations.size() > 4) {
            box.text("... " + (relations.size() - 4) + " more");
            box.NL();
        }

        box.NL(6);
        box.text("Requests: " + requests.size());
        box.NL();
        for (int i = 0; i < requests.size() && i < 4; i++) {
            WarRequest request = requests.get(i);
            box.text((i + 1) + ". " + request.status() + " " + request.fromPlayer() + " -> " + request.toPlayer());
            box.NL();
        }
        if (requests.size() > 4) {
            box.text("... " + (requests.size() - 4) + " more");
            box.NL();
        }

        box.NL(6);
        box.text("Reports: " + reports.size());
        box.NL();
        for (int i = 0; i < reports.size() && i < 6; i++) {
            WarReport report = reports.get(i);
            String result = report.playerWon(runtime.config().playerName()) ? "WIN" : "LOSS";
            box.text((i + 1) + ". " + result + " " + report.attacker() + " vs " + report.defender());
            box.NL();
            box.text("   " + report.attackerRoll() + " - " + report.defenderRoll() + " margin=" + report.margin());
            box.NL();
            box.text("   est losses " + report.attacker() + ":" + report.attackerEstimatedLosses()
                + " " + report.defender() + ":" + report.defenderEstimatedLosses());
            box.NL();
        }
        if (reports.size() > 6) {
            box.text("... " + (reports.size() - 6) + " more");
            box.NL();
        }
        box.NL(6);
        box.text("Click to accept an incoming request, continue an active war, or send a request to the detected friend.");
    }

    private String labelText(
        WarStatus status,
        WarReport latest,
        WarRequest incoming,
        WarRequest outgoing,
        DiplomacyRelation relation
    ) {
        if (status.state() == WarStatus.State.IN_FLIGHT) {
            return "WAR ...";
        }
        if (status.state() == WarStatus.State.FAILED) {
            return "WAR ERR";
        }
        if (incoming != null) {
            return "WAR IN";
        }
        if (outgoing != null) {
            return "WAR OUT";
        }
        if (relation != null && relation.atWar()) {
            return "WAR ON";
        }
        if (latest != null) {
            return latest.playerWon(runtime.config().playerName()) ? "WAR W" : "WAR L";
        }
        return "WAR";
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
