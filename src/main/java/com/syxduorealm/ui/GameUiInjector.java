package com.syxduorealm.ui;

import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import view.main.VIEW;
import view.sett.invasion.SBattleView;
import view.ui.top.UIPanelTop;

import java.util.Optional;

public final class GameUiInjector {

    public void injectIntoTopPanels(RENDEROBJ element) {
        int injected = 0;
        injected += tryInject("world", () -> injectIntoWorldTopPanel(element)) ? 1 : 0;
        injected += tryInject("settlement", () -> injectIntoSettlementTopPanel(element)) ? 1 : 0;
        injected += tryInject("battle", () -> injectIntoBattleTopPanel(element)) ? 1 : 0;

        if (injected == 0) {
            throw new IllegalStateException("Could not find any top panel to inject the Syx Duo Realm button.");
        }
    }

    private boolean injectIntoWorldTopPanel(RENDEROBJ element) {
        return findInIterable(VIEW.world().uiManager, UIPanelTop.class)
            .flatMap(top -> ReflectionUtil.<GuiSection>getFieldValue("right", top))
            .map(right -> add(right, element))
            .orElse(false);
    }

    private boolean injectIntoSettlementTopPanel(RENDEROBJ element) {
        return findInIterable(VIEW.s().uiManager, UIPanelTop.class)
            .flatMap(top -> ReflectionUtil.<GuiSection>getFieldValue("right", top))
            .map(right -> add(right, element))
            .orElse(false);
    }

    private boolean injectIntoBattleTopPanel(RENDEROBJ element) {
        SBattleView battle = VIEW.s().battle;
        return findInIterable(battle.uiManager, UIPanelTop.class)
            .flatMap(top -> ReflectionUtil.<GuiSection>getFieldValue("right", top))
            .map(right -> add(right, element))
            .orElse(false);
    }

    private boolean add(GuiSection right, RENDEROBJ element) {
        right.addRelBody(8, DIR.W, element);
        return true;
    }

    private boolean tryInject(String name, Injection injection) {
        try {
            return injection.run();
        } catch (Throwable throwable) {
            System.err.println("[Syx Duo Realm] Could not inject button into " + name + " top panel");
            throwable.printStackTrace(System.err);
            return false;
        }
    }

    private <T> Optional<T> findInIterable(Object uiManager, Class<T> type) {
        Optional<Iterable<?>> inters = ReflectionUtil.getFieldValue("inters", uiManager);
        if (inters.isEmpty()) {
            return Optional.empty();
        }

        for (Object inter : inters.get()) {
            if (type.isInstance(inter)) {
                return Optional.of(type.cast(inter));
            }
        }

        return Optional.empty();
    }

    private interface Injection {
        boolean run();
    }
}
