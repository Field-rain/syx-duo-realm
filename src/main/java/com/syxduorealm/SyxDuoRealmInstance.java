package com.syxduorealm;

import com.syxduorealm.lifecycle.PhaseManager;
import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import view.main.VIEW;

import java.io.IOException;

final class SyxDuoRealmInstance implements SCRIPT.SCRIPT_INSTANCE {

    private final PhaseManager phaseManager;
    private boolean gameUpdating;
    private boolean gameUiPresent;
    private boolean settlementUiPresent;

    SyxDuoRealmInstance(PhaseManager phaseManager) {
        this.phaseManager = phaseManager;
    }

    @Override
    public void update(double deltaSeconds) {
        if (!gameUpdating) {
            gameUpdating = true;
            phaseManager.initGameUpdating();
        }

        phaseManager.onGameUpdate(deltaSeconds);

        if (!gameUiPresent && gameUiIsPresent()) {
            gameUiPresent = true;
            phaseManager.initGameUiPresent();
        }

        if (!settlementUiPresent && settlementUiIsPresent()) {
            settlementUiPresent = true;
            phaseManager.initSettlementUiPresent();
        }
    }

    @Override
    public void save(FilePutter file) {
        phaseManager.onGameSaved(file.path, file);
    }

    @Override
    public void load(FileGetter file) throws IOException {
        phaseManager.onGameLoaded(file.path, file);
    }

    @Override
    public boolean handleBrokenSavedState() {
        return true;
    }

    private boolean gameUiIsPresent() {
        try {
            return VIEW.inters() != null && !VIEW.inters().load.isActivated();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean settlementUiIsPresent() {
        try {
            return VIEW.s() != null && VIEW.s().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
