package com.syxduorealm.ui;

import com.syxduorealm.SyxDuoRealmRuntime;
import com.syxduorealm.export.ExportStatus;
import com.syxduorealm.sync.SyncStatus;
import com.syxduorealm.trade.TradePackage;
import com.syxduorealm.trade.TradeStatus;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GButt;

import java.util.List;

public final class SyxDuoRealmButton extends GButt.ButtPanel {

    private final SyxDuoRealmRuntime runtime;
    private String lastLabel = "";

    public SyxDuoRealmButton(SyxDuoRealmRuntime runtime) {
        super("D --");
        this.runtime = runtime;
        setDim(58, 24);
        clickActionSet(runtime::primaryAction);
        hoverTitleSet(SyxDuoRealmRuntime.MOD_NAME);
    }

    @Override
    protected void renAction() {
        ExportStatus status = runtime.lastStatus();
        SyncStatus syncStatus = runtime.lastSyncStatus();
        String labelText = labelText(status, syncStatus, runtime.pendingTradeCount(), runtime.selectedTradeIndex());
        if (!labelText.equals(lastLabel)) {
            lastLabel = labelText;
            label = UI.FONT().S.getText(labelText);
        }
        selectedSet(status.state() == ExportStatus.State.SUCCESS && syncStatus.state() == SyncStatus.State.SUCCESS);
    }

    @Override
    public void hoverInfoGet(GUI_BOX box) {
        ExportStatus status = runtime.lastStatus();
        SyncStatus syncStatus = runtime.lastSyncStatus();
        TradeStatus tradeStatus = runtime.lastTradeStatus();
        List<TradePackage> trades = runtime.pendingTrades();
        int selectedTradeIndex = runtime.selectedTradeIndex();
        box.title(SyxDuoRealmRuntime.MOD_NAME);
        box.text("Local city state export + HTTP sync");
        box.NL(6);
        box.text("Config: " + runtime.config().path());
        box.NL();
        box.text("Server: " + runtime.config().serverUrl());
        box.NL();
        box.text("Player: " + runtime.config().playerName());
        box.NL();
        box.text("Room: " + runtime.config().roomCode());
        box.NL();
        box.text("Interval: " + runtime.config().syncIntervalSeconds() + " seconds");
        box.NL(6);
        box.text("Config status: " + runtime.configMessage());
        box.NL();
        if (runtime.configError() != null) {
            box.text("Config error: " + runtime.configError());
            box.NL();
        }
        box.NL(6);
        box.text("Export: " + status.state());
        box.NL();
        box.text("Path: " + status.outputPath());
        box.NL();
        if (status.timestamp() != null) {
            box.text("Time: " + status.timestamp());
            box.NL();
        }
        box.text(status.message());
        box.NL();
        if (status.error() != null) {
            box.text("Error: " + status.error());
            box.NL();
        }
        box.NL(6);
        box.text("HTTP sync: " + syncStatus.state());
        box.NL();
        box.text(syncStatus.message());
        box.NL();
        if (syncStatus.timestamp() != null) {
            box.text("Time: " + syncStatus.timestamp());
            box.NL();
        }
        if (syncStatus.statusCode() != null) {
            box.text("HTTP: " + syncStatus.statusCode());
            box.NL();
        }
        if (syncStatus.error() != null) {
            box.text("Error: " + syncStatus.error());
            box.NL();
        }
        box.NL(6);
        box.text("Trade inbox: " + tradeStatus.state());
        box.NL();
        box.text(tradeStatus.message());
        box.NL();
        if (tradeStatus.timestamp() != null) {
            box.text("Time: " + tradeStatus.timestamp());
            box.NL();
        }
        if (tradeStatus.statusCode() != null) {
            box.text("HTTP: " + tradeStatus.statusCode());
            box.NL();
        }
        if (tradeStatus.error() != null) {
            box.text("Error: " + tradeStatus.error());
            box.NL();
        }
        box.text("Claimed file: " + runtime.claimedTradePath());
        box.NL(6);
        box.text("Pending trades: " + trades.size());
        box.NL();
        for (int i = 0; i < trades.size() && i < 8; i++) {
            TradePackage trade = trades.get(i);
            String marker = i == selectedTradeIndex ? "> " : "  ";
            box.text(marker + (i + 1) + ". " + trade.resourceKey() + " x" + trade.amount() + " from " + trade.fromPlayer());
            box.NL();
        }
        if (trades.size() > 8) {
            box.text("... " + (trades.size() - 8) + " more");
            box.NL();
        }
        box.NL(6);
        box.text(trades.isEmpty() ? "Click to export and sync now." : "Click to claim the selected pending trade. Use N to choose.");
    }

    private String labelText(ExportStatus exportStatus, SyncStatus syncStatus, int pendingTradeCount, int selectedTradeIndex) {
        if (pendingTradeCount > 0) {
            int humanIndex = Math.max(1, selectedTradeIndex + 1);
            return pendingTradeCount > 9 ? "T " + humanIndex + "/9+" : "T " + humanIndex + "/" + pendingTradeCount;
        }
        if (exportStatus.state() == ExportStatus.State.FAILED || syncStatus.state() == SyncStatus.State.FAILED) {
            return "D ERR";
        }
        if (syncStatus.state() == SyncStatus.State.IN_FLIGHT) {
            return "D ...";
        }
        if (exportStatus.state() == ExportStatus.State.SUCCESS && syncStatus.state() == SyncStatus.State.SUCCESS) {
            return "D OK";
        }
        if (exportStatus.state() == ExportStatus.State.SUCCESS) {
            return "D LOC";
        }
        return "D --";
    }
}
