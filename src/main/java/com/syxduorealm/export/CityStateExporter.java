package com.syxduorealm.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class CityStateExporter {

    private final CityStateCollector collector;
    private final Path outputPath;

    public CityStateExporter(CityStateCollector collector, Path outputPath) {
        this.collector = collector;
        this.outputPath = outputPath;
    }

    public CityStateExportResult export(String saveId, String playerName, String roomCode) {
        try {
            CityState state = collector.collect(saveId, playerName, roomCode);
            String json = CityStateJsonWriter.write(state);
            write(json);
            ExportStatus status = ExportStatus.success(outputPath, "Exported city_state.json");
            return CityStateExportResult.success(status, state, json);
        } catch (Exception e) {
            return CityStateExportResult.failure(ExportStatus.failure(outputPath, "Export failed.", e));
        }
    }

    public Path outputPath() {
        return outputPath;
    }

    private void write(String json) throws IOException {
        Files.createDirectories(outputPath.getParent());

        Path tempPath = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
        Files.writeString(
            tempPath,
            json,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );

        try {
            Files.move(
                tempPath,
                outputPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
