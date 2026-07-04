package com.syxduorealm.lifecycle;

import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

public final class PhaseManager implements Phases {

    private final EnumMap<Phase, List<Phases>> phases = new EnumMap<>(Phase.class);

    public PhaseManager() {
        for (Phase phase : Phase.values()) {
            phases.put(phase, new ArrayList<>());
        }
    }

    public PhaseManager register(Phase phase, Phases handler) {
        List<Phases> handlers = phases.get(phase);
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        }
        return this;
    }

    @Override
    public void onGameLoaded(Path saveFilePath, FileGetter fileGetter) {
        execute(Phase.ON_GAME_SAVE_LOADED, phase -> phase.onGameLoaded(saveFilePath, fileGetter));
    }

    @Override
    public void onGameSaved(Path saveFilePath, FilePutter filePutter) {
        execute(Phase.ON_GAME_SAVED, phase -> phase.onGameSaved(saveFilePath, filePutter));
    }

    @Override
    public void initGameUpdating() {
        execute(Phase.INIT_GAME_UPDATING, Phases::initGameUpdating);
    }

    @Override
    public void onGameUpdate(double seconds) {
        execute(Phase.ON_GAME_UPDATE, phase -> phase.onGameUpdate(seconds));
    }

    @Override
    public void initGameUiPresent() {
        execute(Phase.INIT_GAME_UI_PRESENT, Phases::initGameUiPresent);
    }

    @Override
    public void initSettlementUiPresent() {
        execute(Phase.INIT_SETTLEMENT_UI_PRESENT, Phases::initSettlementUiPresent);
    }

    private void execute(Phase phase, Consumer<Phases> action) {
        for (Phases handler : phases.get(phase)) {
            try {
                action.accept(handler);
            } catch (Exception e) {
                System.err.println("[Syx Duo Realm] Phase failed: " + phase + " in " + handler.getClass().getName());
                e.printStackTrace(System.err);
            }
        }
    }
}
