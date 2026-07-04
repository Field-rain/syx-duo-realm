package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.trade.TradePackage;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

public final class SyxTradeSelectButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxTradeSelectButton(SyxDuoRealmRuntime runtime) {
        super("N --");
        this.runtime = runtime;
        setDim(52, 24);
        clickActionSet(runtime::selectNextPendingTrade);
        hoverTitleSet("Syx Duo Realm Trade Select");
    }

    @Override
    protected void renAction() {
        String labelText = labelText(runtime.pendingTradeCount(), runtime.selectedTradeIndex());
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(runtime.pendingTradeCount() > 0);
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        TradePackage selected = runtime.selectedPendingTrade();
        int count = runtime.pendingTradeCount();
        int index = runtime.selectedTradeIndex();

        box.title("Syx Duo Realm Trade Select");
        box.text("Pending trades: " + count);
        box.NL();
        if (selected == null) {
            box.text("No pending trade package.");
            box.NL(6);
            box.text("Click DUO to export and sync now.");
            return;
        }

        box.text("Selected: " + (index + 1) + "/" + count);
        box.NL();
        box.text("Resource: " + selected.resourceKey());
        box.NL();
        box.text("Amount: " + selected.amount());
        box.NL();
        box.text("From: " + selected.fromPlayer());
        box.NL();
        box.text("Trade id: " + selected.tradeId());
        box.NL(6);
        box.text("Click N to choose the next package.");
        box.NL();
        box.text("Click the D/T button to claim the selected package.");
    }

    private String labelText(int pendingTradeCount, int selectedIndex) {
        if (pendingTradeCount <= 0 || selectedIndex < 0) {
            return "N --";
        }
        int humanIndex = selectedIndex + 1;
        if (pendingTradeCount > 9) {
            return "N " + humanIndex + "/9+";
        }
        return "N " + humanIndex + "/" + pendingTradeCount;
    }
}
