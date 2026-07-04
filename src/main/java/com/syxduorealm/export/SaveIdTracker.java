package com.syxduorealm.export;

import java.nio.file.Path;

public final class SaveIdTracker {

    private String current = "unsaved";

    public void update(Path savePath) {
        if (savePath == null || savePath.getFileName() == null) {
            current = "unsaved";
            return;
        }

        String fileName = savePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        current = dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public String current() {
        return current;
    }
}
