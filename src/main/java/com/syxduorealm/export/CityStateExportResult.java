package com.syxduorealm.export;

public final class CityStateExportResult {

    private final ExportStatus status;
    private final CityState state;
    private final String json;

    private CityStateExportResult(ExportStatus status, CityState state, String json) {
        this.status = status;
        this.state = state;
        this.json = json;
    }

    public static CityStateExportResult success(ExportStatus status, CityState state, String json) {
        return new CityStateExportResult(status, state, json);
    }

    public static CityStateExportResult failure(ExportStatus status) {
        return new CityStateExportResult(status, null, null);
    }

    public ExportStatus status() {
        return status;
    }

    public CityState state() {
        return state;
    }

    public String json() {
        return json;
    }

    public boolean success() {
        return state != null && status.state() == ExportStatus.State.SUCCESS;
    }
}
