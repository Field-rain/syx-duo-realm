package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import util.gui.misc.GButt;

public final class SyxDuoRealmPanel extends GuiSection {

    private static final int GAP = 4;

    private final ToggleButton toggleButton = new ToggleButton();
    private final SyxDuoRealmButton actionButton;
    private final SyxTradeSelectButton tradeSelectButton;
    private final SyxTradeResourceButton tradeResourceButton;
    private final SyxTradeAmountButton tradeAmountButton;
    private final SyxTradeSendButton tradeSendButton;
    private final SyxWarButton warButton;
    private final SyxPeaceButton peaceButton;
    private final SyxRoomStatusButton roomButton;
    private final SyxShadowButton shadowButton;
    private boolean expanded;

    public SyxDuoRealmPanel(SyxDuoRealmRuntime runtime) {
        actionButton = new SyxDuoRealmButton(runtime);
        tradeSelectButton = new SyxTradeSelectButton(runtime);
        tradeResourceButton = new SyxTradeResourceButton(runtime);
        tradeAmountButton = new SyxTradeAmountButton(runtime);
        tradeSendButton = new SyxTradeSendButton(runtime);
        warButton = new SyxWarButton(runtime);
        peaceButton = new SyxPeaceButton(runtime);
        roomButton = new SyxRoomStatusButton(runtime);
        shadowButton = new SyxShadowButton(runtime);
        layoutButtons(0, 0, false);
    }

    private void toggleExpanded() {
        int right = body().x2();
        int y = body().y1();
        expanded = !expanded;
        layoutButtons(right, y, true);
    }

    private void layoutButtons(int right, int y, boolean keepRight) {
        clear();

        int toggleWidth = toggleButton.body().width();
        int x = keepRight ? right - toggleWidth : 0;
        toggleButton.body().moveX1Y1(x, y);
        add(toggleButton);

        if (!expanded) {
            return;
        }

        RENDEROBJ anchor = toggleButton;
        anchor = addLeft(anchor, actionButton);
        anchor = addLeft(anchor, tradeSelectButton);
        anchor = addLeft(anchor, tradeResourceButton);
        anchor = addLeft(anchor, tradeAmountButton);
        anchor = addLeft(anchor, tradeSendButton);
        anchor = addLeft(anchor, warButton);
        anchor = addLeft(anchor, peaceButton);
        anchor = addLeft(anchor, roomButton);
        addLeft(anchor, shadowButton);
    }

    private RENDEROBJ addLeft(RENDEROBJ anchor, RENDEROBJ button) {
        button.body().moveX1(anchor.body().x1() - GAP - button.body().width());
        button.body().centerY(anchor.body());
        add(button);
        return button;
    }

    private final class ToggleButton extends GButt.ButtPanel {

        private String lastLabel = "";

        private ToggleButton() {
            super("DUO");
            setDim(64, 24);
            clickActionSet(SyxDuoRealmPanel.this::toggleExpanded);
            hoverTitleSet(SyxDuoRealmRuntime.MOD_NAME);
        }

        @Override
        protected void renAction() {
            String labelText = expanded ? "DUO <" : "DUO >";
            if (!labelText.equals(lastLabel)) {
                lastLabel = labelText;
                label = UI.FONT().S.getText(labelText);
            }
            selectedSet(expanded);
        }

        @Override
        public void hoverInfoGet(GUI_BOX box) {
            box.title(SyxDuoRealmRuntime.MOD_NAME);
            box.text(expanded ? "Click to collapse controls." : "Click to expand controls.");
            box.NL(6);
            box.text("Expanded controls:");
            box.NL();
            box.text("D: export/sync or claim selected trade");
            box.NL();
            box.text("N: select next pending trade");
            box.NL();
            box.text("RES: cycle outgoing trade resource");
            box.NL();
            box.text("AMT: cycle outgoing trade amount");
            box.NL();
            box.text("SND: send configured trade");
            box.NL();
            box.text("WAR: request, accept, or continue server war");
            box.NL();
            box.text("PCE: request or accept server peace");
            box.NL();
            box.text("R: refresh room status");
            box.NL();
            box.text("SHD: create/remove friend shadow faction");
        }
    }
}
