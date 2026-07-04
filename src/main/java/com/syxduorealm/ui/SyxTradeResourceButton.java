package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

public final class SyxTradeResourceButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxTradeResourceButton(SyxDuoRealmRuntime runtime) {
        super("RES");
        this.runtime = runtime;
        setDim(58, 24);
        clickActionSet(runtime::cycleTradeOfferResource);
        hoverTitleSet("Syx Duo Realm Trade Resource");
    }

    @Override
    protected void renAction() {
        String key = runtime.config().tradeOfferResourceKey();
        String labelText = "RES";
        if (key != null && !key.isBlank()) {
            labelText = key.length() <= 5 ? key : key.substring(0, 5);
        }
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(runtime.configuredTradeAvailable() > 0);
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        box.title("Syx Duo Realm Trade Resource");
        box.text("Outgoing trade resource");
        box.NL(6);
        box.text("Key: " + runtime.config().tradeOfferResourceKey());
        box.NL();
        if (!runtime.configuredTradeResourceName().isBlank()) {
            box.text("Name: " + runtime.configuredTradeResourceName());
            box.NL();
        }
        box.text("Reservable: " + runtime.configuredTradeAvailable());
        box.NL();
        box.text("Amount: " + runtime.config().tradeOfferAmount());
        box.NL(6);
        box.text("Click to cycle to the next resource with reservable stock.");
        box.NL();
        box.text("The choice is saved to syx_duo_realm.json.");
    }
}
