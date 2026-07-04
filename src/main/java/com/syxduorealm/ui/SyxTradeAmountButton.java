package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

public final class SyxTradeAmountButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxTradeAmountButton(SyxDuoRealmRuntime runtime) {
        super("AMT");
        this.runtime = runtime;
        setDim(58, 24);
        clickActionSet(runtime::cycleTradeOfferAmount);
        hoverTitleSet("Syx Duo Realm Trade Amount");
    }

    @Override
    protected void renAction() {
        int amount = runtime.config().tradeOfferAmount();
        String labelText = amount >= 1000 ? "A" + (amount / 1000) + "K" : "A" + amount;
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(runtime.configuredTradeAvailable() >= amount);
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        box.title("Syx Duo Realm Trade Amount");
        box.text("Outgoing trade amount");
        box.NL(6);
        box.text("Resource: " + runtime.config().tradeOfferResourceKey());
        box.NL();
        box.text("Amount: " + runtime.config().tradeOfferAmount());
        box.NL();
        box.text("Reservable: " + runtime.configuredTradeAvailable());
        box.NL(6);
        box.text("Click to cycle amount: 25, 50, 100, 250, 500, 1000.");
        box.NL();
        box.text("The choice is saved to syx_duo_realm.json.");
    }
}
