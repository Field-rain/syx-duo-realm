package com.syxduorealm.lifecycle;

import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

import java.nio.file.Path;

public interface Phases {

    default void onGameLoaded(Path saveFilePath, FileGetter fileGetter) {
    }

    default void onGameSaved(Path saveFilePath, FilePutter filePutter) {
    }

    default void initGameUpdating() {
    }

    default void onGameUpdate(double seconds) {
    }

    default void initGameUiPresent() {
    }

    default void initSettlementUiPresent() {
    }
}
