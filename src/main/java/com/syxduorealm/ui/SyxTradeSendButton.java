package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.trade.SentTradeRecord;
import com.syxduorealm.trade.TradeStatus;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

public final class SyxTradeSendButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxTradeSendButton(SyxDuoRealmRuntime runtime) {
        super("SND");
        this.runtime = runtime;
        setDim(62, 24);
        clickActionSet(runtime::sendTradeOffer);
        hoverTitleSet("Syx Duo Realm Trade Send");
    }

    @Override
    protected void renAction() {
        TradeStatus status = runtime.lastTradeSendStatus();
        String labelText = labelText(status);
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(status.state() == TradeStatus.State.SUCCESS);
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        TradeStatus status = runtime.lastTradeSendStatus();

        box.title("Syx Duo Realm Trade Send");
        box.text("Configured outgoing trade");
        box.NL(6);
        box.text("Resource key: " + runtime.config().tradeOfferResourceKey());
        box.NL();
        if (!runtime.configuredTradeResourceName().isBlank()) {
            box.text("Resource name: " + runtime.configuredTradeResourceName());
            box.NL();
        }
        box.text("Amount: " + runtime.config().tradeOfferAmount());
        box.NL();
        box.text("Reservable: " + runtime.configuredTradeAvailable());
        box.NL();
        box.text("Configured toPlayer: " + blankFallback(runtime.config().tradeOfferToPlayer(), "(auto friend)"));
        box.NL();
        box.text("Resolved toPlayer: " + blankFallback(runtime.tradeOfferRecipient(), "(none)"));
        box.NL();
        box.text("Target source: " + runtime.friendTargetSource());
        box.NL(6);
        box.text("Last send: " + status.state());
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
        if (!runtime.lastSentTradeId().isBlank()) {
            box.text("Last trade id: " + runtime.lastSentTradeId());
            box.NL();
        }
        SentTradeRecord latest = runtime.latestSentTrade();
        box.text("Deducted pending: " + runtime.pendingSentTradeCount());
        box.NL();
        if (latest != null) {
            box.text("Latest local: " + latest.status() + " " + latest.resourceKey() + " x" + latest.amount());
            box.NL();
            box.text("Latest id: " + latest.localTradeId());
            box.NL();
        }
        box.text("Ledger: " + runtime.sentTradePath());
        box.NL();
        box.NL(6);
        box.text("Click to deduct and send this offer to the resolved player.");
        box.NL();
        box.text("HTTP failure refunds the deducted resources near the throne.");
    }

    private String labelText(TradeStatus status) {
        if (status.state() == TradeStatus.State.IN_FLIGHT) {
            return "SND ...";
        }
        if (status.state() == TradeStatus.State.FAILED) {
            return "SND ERR";
        }
        if (status.state() == TradeStatus.State.SUCCESS) {
            return "SND OK";
        }
        return "SND";
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
