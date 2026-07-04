package com.syxduorealm.export;

public final class ExportScheduler {

    private final long periodMillis;
    private long lastRunMillis = Long.MIN_VALUE;

    public ExportScheduler(long periodMillis) {
        this.periodMillis = periodMillis;
    }

    public void reset() {
        lastRunMillis = Long.MIN_VALUE;
    }

    public boolean shouldRun(long nowMillis) {
        if (lastRunMillis == Long.MIN_VALUE) {
            lastRunMillis = nowMillis;
            return true;
        }

        if (nowMillis - lastRunMillis >= periodMillis) {
            lastRunMillis = nowMillis;
            return true;
        }

        return false;
    }
}
