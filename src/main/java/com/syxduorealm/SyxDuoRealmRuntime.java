package com.syxduorealm;

import com.syxduorealm.config.SyxDuoRealmConfig;
import com.syxduorealm.export.CityStateExportResult;
import com.syxduorealm.export.CityStateCollector;
import com.syxduorealm.export.CityStateExporter;
import com.syxduorealm.export.ExportScheduler;
import com.syxduorealm.export.ExportStatus;
import com.syxduorealm.export.SaveIdTracker;
import com.syxduorealm.lifecycle.Phases;
import com.syxduorealm.room.RoomState;
import com.syxduorealm.room.RoomStatus;
import com.syxduorealm.room.RoomStatusClient;
import com.syxduorealm.shadow.FriendState;
import com.syxduorealm.shadow.ShadowBinding;
import com.syxduorealm.shadow.ShadowBindingStore;
import com.syxduorealm.shadow.ShadowClient;
import com.syxduorealm.shadow.ShadowFactionView;
import com.syxduorealm.shadow.ShadowFactionManager;
import com.syxduorealm.shadow.ShadowStatus;
import com.syxduorealm.sync.SyncClient;
import com.syxduorealm.sync.SyncStatus;
import com.syxduorealm.trade.ClaimedTradeStore;
import com.syxduorealm.trade.SentTradeRecord;
import com.syxduorealm.trade.SentTradeStore;
import com.syxduorealm.trade.TradeEscrowService;
import com.syxduorealm.trade.TradeInboxClient;
import com.syxduorealm.trade.TradePackage;
import com.syxduorealm.trade.TradeResourceDropper;
import com.syxduorealm.trade.TradeSendClient;
import com.syxduorealm.trade.TradeStatus;
import com.syxduorealm.ui.GameUiInjector;
import com.syxduorealm.ui.SyxDuoRealmPanel;
import com.syxduorealm.war.WarClient;
import com.syxduorealm.war.DiplomacyRelation;
import com.syxduorealm.war.PeaceRequest;
import com.syxduorealm.war.WarReport;
import com.syxduorealm.war.WarRequest;
import com.syxduorealm.war.WarStatus;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.paths.PATHS;
import settlement.main.SETT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import util.gui.misc.GBox;
import view.main.VIEW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class SyxDuoRealmRuntime implements Phases {

    public static final String MOD_NAME = "Syx Duo Realm";

    private final SaveIdTracker saveIdTracker = new SaveIdTracker();
    private final Path dataDirectory;
    private final Path configPath;
    private final CityStateExporter exporter;
    private final ExportScheduler scheduler;
    private final ExportScheduler tradeScheduler;
    private final ExportScheduler roomScheduler;
    private final ExportScheduler warScheduler;
    private final ExportScheduler shadowScheduler;
    private final SyxDuoRealmConfig config;
    private final SyncClient syncClient;
    private final TradeInboxClient tradeClient = new TradeInboxClient();
    private final TradeSendClient tradeSendClient = new TradeSendClient();
    private final RoomStatusClient roomClient = new RoomStatusClient();
    private final TradeResourceDropper tradeDropper = new TradeResourceDropper();
    private final TradeEscrowService tradeEscrow = new TradeEscrowService();
    private final ClaimedTradeStore claimedTradeStore;
    private final SentTradeStore sentTradeStore;
    private final ShadowClient shadowClient = new ShadowClient();
    private final WarClient warClient = new WarClient();
    private final ShadowBindingStore shadowBindingStore;
    private final ShadowFactionManager shadowFactionManager;
    private final GameUiInjector uiInjector = new GameUiInjector();

    private ExportStatus lastStatus;
    private String configMessage;
    private String configError;
    private String lastSyncNotificationKey = "";
    private boolean lastNotifiedSyncWasFailure;
    private String lastShadowNotificationKey = "";
    private String lastNativeDiplomacyMirrorKey = "";
    private boolean shadowApplyRequested;
    private boolean shadowApplyManual;
    private PendingSentTrade pendingSentTrade;
    private int selectedTradeIndex;
    private boolean uiInstalled;

    public SyxDuoRealmRuntime() {
        dataDirectory = PATHS.local().PROFILE.get().resolve(MOD_NAME);
        configPath = dataDirectory.resolve("syx_duo_realm.json");
        SyxDuoRealmConfig.LoadResult configResult = SyxDuoRealmConfig.loadOrCreate(configPath);
        config = configResult.config();
        configMessage = configResult.message();
        configError = configResult.error() == null ? null : configResult.error().getClass().getSimpleName() + ": " + configResult.error().getMessage();

        if (configResult.error() != null) {
            System.err.println("[Syx Duo Realm] " + configResult.message());
            configResult.error().printStackTrace(System.err);
        } else {
            System.out.println("[Syx Duo Realm] " + configResult.message() + " " + config.path());
        }

        Path outputPath = dataDirectory.resolve("city_state.json");
        exporter = new CityStateExporter(new CityStateCollector(), outputPath);
        scheduler = new ExportScheduler(config.syncIntervalMillis());
        tradeScheduler = new ExportScheduler(config.syncIntervalMillis());
        roomScheduler = new ExportScheduler(config.syncIntervalMillis());
        warScheduler = new ExportScheduler(config.syncIntervalMillis());
        shadowScheduler = new ExportScheduler(config.syncIntervalMillis());
        syncClient = new SyncClient(config.serverUrl());
        claimedTradeStore = new ClaimedTradeStore(dataDirectory.resolve("claimed_trades.json"));
        sentTradeStore = new SentTradeStore(dataDirectory.resolve("sent_trades.json"));
        shadowBindingStore = new ShadowBindingStore(dataDirectory.resolve("shadow_binding.json"));
        shadowFactionManager = new ShadowFactionManager(shadowBindingStore);
        lastStatus = ExportStatus.notRun(outputPath);
    }

    @Override
    public void onGameLoaded(Path saveFilePath, FileGetter fileGetter) {
        saveIdTracker.update(saveFilePath);
        scheduler.reset();
        tradeScheduler.reset();
        roomScheduler.reset();
        warScheduler.reset();
        shadowScheduler.reset();
        System.out.println("[Syx Duo Realm] loaded save: " + saveIdTracker.current());
    }

    @Override
    public void onGameSaved(Path saveFilePath, FilePutter filePutter) {
        saveIdTracker.update(saveFilePath);
    }

    @Override
    public void initGameUpdating() {
        scheduler.reset();
        tradeScheduler.reset();
        roomScheduler.reset();
        warScheduler.reset();
        shadowScheduler.reset();
    }

    @Override
    public void onGameUpdate(double seconds) {
        publishSyncNotificationIfNeeded();

        if (!SETT.exists()) {
            return;
        }

        processPendingTradeSendResult();
        applyPendingShadowStateIfReady();
        mirrorNativeDiplomacyIfPossible();

        long nowMillis = System.currentTimeMillis();

        if (tradeScheduler.shouldRun(nowMillis)) {
            refreshTradeInbox();
        }

        if (roomScheduler.shouldRun(nowMillis)) {
            refreshRoomStatus();
        }

        if (warScheduler.shouldRun(nowMillis)) {
            refreshWarReports();
        }

        if (shadowScheduler.shouldRun(nowMillis)) {
            refreshShadowProjectionIfActive();
        }

        if (scheduler.shouldRun(nowMillis)) {
            exportNow();
        }
    }

    @Override
    public void initGameUiPresent() {
        installUi();
    }

    @Override
    public void initSettlementUiPresent() {
        installUi();
    }

    public synchronized ExportStatus exportNow() {
        if (!SETT.exists()) {
            lastStatus = ExportStatus.failure(
                exporter.outputPath(),
                "Settlement is not loaded yet.",
                null
            );
            return lastStatus;
        }

        CityStateExportResult result = exporter.export(
            saveIdTracker.current(),
            config.playerName(),
            config.roomCode()
        );
        lastStatus = result.status();
        System.out.println("[Syx Duo Realm] export " + lastStatus.state() + ": " + lastStatus.message());

        if (result.success()) {
            SyncStatus syncStatus = syncClient.syncAsync(config, result.state());
            System.out.println("[Syx Duo Realm] sync " + syncStatus.state() + ": " + syncStatus.message());
        }

        return lastStatus;
    }

    public void primaryAction() {
        if (pendingTradeCount() > 0) {
            claimSelectedPendingTrade();
        } else {
            exportNow();
        }
    }

    public synchronized void refreshTradeInbox() {
        TradeStatus status = tradeClient.refreshAsync(config, claimedTradeStore.snapshot());
        System.out.println("[Syx Duo Realm] trade inbox " + status.state() + ": " + status.message());
    }

    public synchronized void refreshRoomStatus() {
        RoomStatus status = roomClient.refreshAsync(config);
        System.out.println("[Syx Duo Realm] room status " + status.state() + ": " + status.message());
    }

    public synchronized void refreshWarReports() {
        WarStatus status = warClient.refreshReportsAsync(config);
        System.out.println("[Syx Duo Realm] war reports " + status.state() + ": " + status.message());
    }

    public synchronized void roomPrimaryAction() {
        RoomStatus status = roomClient.refreshAsync(config);
        System.out.println("[Syx Duo Realm] room status " + status.state() + ": " + status.message());
        if (status.state() == RoomStatus.State.FAILED) {
            sendGameMessage("Syx Duo Realm room failed", status.message() + "\n" + nullToEmpty(status.error()));
        }
    }

    public synchronized void claimFirstPendingTrade() {
        claimSelectedPendingTrade();
    }

    public synchronized void claimSelectedPendingTrade() {
        TradePackage trade = selectedPendingTrade();
        if (trade == null) {
            sendGameMessage(MOD_NAME, "No pending trade package.");
            return;
        }

        if (claimedTradeStore.contains(trade.tradeId())) {
            tradeClient.removeFromInbox(trade.tradeId());
            sendGameMessage(MOD_NAME, "Trade already claimed locally: " + trade.tradeId());
            return;
        }

        TradeResourceDropper.DropResult drop = tradeDropper.drop(trade);
        if (!drop.success()) {
            sendGameMessage("Syx Duo Realm trade failed", drop.error());
            return;
        }

        try {
            claimedTradeStore.markClaimed(trade.tradeId());
        } catch (IOException e) {
            sendGameMessage("Syx Duo Realm trade warning", "Resource was placed, but claimed_trades.json could not be saved.");
            System.err.println("[Syx Duo Realm] Could not persist claimed trade " + trade.tradeId());
            e.printStackTrace(System.err);
        }

        tradeClient.removeFromInbox(trade.tradeId());
        clampSelectedTradeIndex();
        TradeStatus claimStatus = tradeClient.postClaimAsync(config, trade);
        sendGameMessage(
            "Syx Duo Realm trade claimed",
            drop.resourceName() + " x" + drop.amount() + "\nFrom: " + trade.fromPlayer() + "\n" + claimStatus.message()
        );
    }

    public synchronized void selectNextPendingTrade() {
        List<TradePackage> trades = pendingTrades();
        if (trades.isEmpty()) {
            selectedTradeIndex = 0;
            sendGameMessage(MOD_NAME, "No pending trade package to select.");
            return;
        }

        selectedTradeIndex = (selectedTradeIndex + 1) % trades.size();
        TradePackage trade = trades.get(selectedTradeIndex);
        sendGameMessage(
            "Syx Duo Realm trade selected",
            (selectedTradeIndex + 1) + "/" + trades.size() + " "
                + trade.resourceKey() + " x" + trade.amount() + "\nFrom: " + trade.fromPlayer()
        );
    }

    public synchronized void sendTradeOffer() {
        if (pendingSentTrade != null || tradeSendClient.lastStatus().state() == TradeStatus.State.IN_FLIGHT) {
            sendGameMessage("Syx Duo Realm trade send", "A trade send request is already in progress.");
            return;
        }

        if (!SETT.exists()) {
            sendGameMessage("Syx Duo Realm trade send failed", "Settlement is not loaded.");
            return;
        }

        RESOURCE resource = configuredTradeResource();
        if (resource == null) {
            sendGameMessage("Syx Duo Realm trade send failed", "Unknown resource key: " + config.tradeOfferResourceKey());
            return;
        }

        int amount = config.tradeOfferAmount();
        int available = SETT.ROOMS().STOCKPILE.tally().amountReservable.get(resource);
        if (available < amount) {
            sendGameMessage(
                "Syx Duo Realm trade send failed",
                resource.name + " reservable=" + available + ", needed=" + amount
            );
            return;
        }

        String recipient = tradeOfferRecipient();
        if (recipient.isBlank()) {
            sendGameMessage("Syx Duo Realm trade send failed", "No friend detected. Refresh ROOM or set tradeOfferToPlayer.");
            return;
        }
        if (recipient.equals(config.playerName())) {
            sendGameMessage("Syx Duo Realm trade send failed", "Recipient is the same as playerName.");
            return;
        }

        String localTradeId = createLocalTradeId(recipient);
        TradeEscrowService.EscrowResult escrow = tradeEscrow.deduct(resource, amount);
        if (!escrow.success()) {
            if (escrow.removed() > 0) {
                TradeResourceDropper.DropResult refund = refundRemovedResource(localTradeId, resource.key, escrow.removed());
                sendGameMessage(
                    "Syx Duo Realm trade send failed",
                    escrow.error() + "\nRefund " + (refund.success() ? "placed near throne." : "failed: " + refund.error())
                );
            } else {
                sendGameMessage("Syx Duo Realm trade send failed", escrow.error());
            }
            return;
        }

        try {
            sentTradeStore.addDeducted(
                localTradeId,
                config.roomCode(),
                config.playerName(),
                recipient,
                resource.key,
                amount,
                available
            );
        } catch (IOException e) {
            TradeResourceDropper.DropResult refund = refundRemovedResource(localTradeId, resource.key, amount);
            System.err.println("[Syx Duo Realm] Could not persist deducted outgoing trade " + localTradeId);
            e.printStackTrace(System.err);
            sendGameMessage(
                "Syx Duo Realm trade send failed",
                "Resources were deducted, but sent_trades.json could not be saved.\n"
                    + "Refund " + (refund.success() ? "placed near throne." : "failed: " + refund.error())
            );
            return;
        }

        PendingSentTrade pending = new PendingSentTrade(localTradeId, recipient, resource.key, amount);
        TradeStatus status = tradeSendClient.sendAsync(config, localTradeId, recipient, resource.key, amount, available);
        System.out.println("[Syx Duo Realm] trade send " + status.state() + ": " + status.message());
        if (status.state() == TradeStatus.State.FAILED) {
            refundAndMarkPendingTrade(pending, "Could not start POST /api/trade.\n" + status.message());
            return;
        }

        pendingSentTrade = pending;
        sendGameMessage(
            "Syx Duo Realm trade send",
            "Deducted " + resource.name + " x" + amount + " locally.\nSending to " + recipient + "."
        );
    }

    public synchronized void shadowPrimaryAction() {
        if (shadowFactionManager.hasActiveBinding()) {
            shadowApplyRequested = false;
            shadowApplyManual = false;
            ShadowFactionManager.CleanupResult result = shadowFactionManager.cleanup();
            sendGameMessage(
                result.success() ? "Syx Duo Realm shadow removed" : "Syx Duo Realm shadow failed",
                result.message()
            );
            return;
        }

        shadowApplyRequested = true;
        shadowApplyManual = true;
        ShadowStatus status = shadowClient.refreshAsync(config);
        System.out.println("[Syx Duo Realm] shadow " + status.state() + ": " + status.message());
        if (status.state() == ShadowStatus.State.FAILED) {
            shadowApplyRequested = false;
            shadowApplyManual = false;
            sendGameMessage("Syx Duo Realm shadow failed", status.message() + "\n" + nullToEmpty(status.error()));
        }
    }

    public synchronized void warPrimaryAction() {
        WarRequest incoming = warClient.incomingRequest(config.playerName());
        if (incoming != null) {
            WarStatus status = warClient.acceptWarRequestAsync(config, incoming);
            System.out.println("[Syx Duo Realm] war accept " + status.state() + ": " + status.message());
            if (status.state() == WarStatus.State.FAILED) {
                sendGameMessage("Syx Duo Realm war failed", status.message() + "\n" + nullToEmpty(status.error()));
                return;
            }

            sendGameMessage(
                "Syx Duo Realm war",
                "Accepting war request from " + incoming.fromPlayer() + ".\nServer will resolve the battle report."
            );
            return;
        }

        String defender = tradeOfferRecipient();
        if (defender.isBlank()) {
            WarStatus status = warClient.refreshReportsAsync(config);
            sendGameMessage("Syx Duo Realm war", "No friend detected. Refreshing war status instead.\n" + status.message());
            return;
        }
        if (defender.equals(config.playerName())) {
            sendGameMessage("Syx Duo Realm war failed", "Defender is the same as playerName.");
            return;
        }

        DiplomacyRelation relation = warClient.relationWith(config.roomCode(), config.playerName(), defender);
        if (relation.atWar()) {
            WarStatus status = warClient.declareWarAsync(config, defender);
            System.out.println("[Syx Duo Realm] war battle " + status.state() + ": " + status.message());
            if (status.state() == WarStatus.State.FAILED) {
                sendGameMessage("Syx Duo Realm war failed", status.message() + "\n" + nullToEmpty(status.error()));
                return;
            }

            sendGameMessage(
                "Syx Duo Realm war",
                "Server relation is WAR with " + defender + ".\nResolving one controlled battle report."
            );
            return;
        }

        WarRequest outgoing = warClient.outgoingRequest(config.playerName());
        if (outgoing != null && defender.equals(outgoing.toPlayer())) {
            WarStatus status = warClient.refreshReportsAsync(config);
            sendGameMessage(
                "Syx Duo Realm war",
                "War request is already pending for " + defender + ".\nRefreshing war status.\n" + status.message()
            );
            return;
        }

        WarStatus status = warClient.sendWarRequestAsync(config, defender);
        System.out.println("[Syx Duo Realm] war request " + status.state() + ": " + status.message());
        if (status.state() == WarStatus.State.FAILED) {
            sendGameMessage("Syx Duo Realm war failed", status.message() + "\n" + nullToEmpty(status.error()));
            return;
        }

        sendGameMessage(
            "Syx Duo Realm war",
            "Sent war request to " + defender + ".\nThey must accept before the server resolves a report."
        );
    }

    public synchronized void peacePrimaryAction() {
        PeaceRequest incoming = warClient.incomingPeaceRequest(config.playerName());
        if (incoming != null) {
            WarStatus status = warClient.acceptPeaceRequestAsync(config, incoming);
            System.out.println("[Syx Duo Realm] peace accept " + status.state() + ": " + status.message());
            if (status.state() == WarStatus.State.FAILED) {
                sendGameMessage("Syx Duo Realm peace failed", status.message() + "\n" + nullToEmpty(status.error()));
                return;
            }

            sendGameMessage(
                "Syx Duo Realm peace",
                "Accepting peace request from " + incoming.fromPlayer() + ".\nServer relation will become PEACE."
            );
            return;
        }

        String target = tradeOfferRecipient();
        if (target.isBlank()) {
            WarStatus status = warClient.refreshReportsAsync(config);
            sendGameMessage("Syx Duo Realm peace", "No friend detected. Refreshing diplomacy status instead.\n" + status.message());
            return;
        }
        if (target.equals(config.playerName())) {
            sendGameMessage("Syx Duo Realm peace failed", "Target is the same as playerName.");
            return;
        }

        DiplomacyRelation relation = warClient.relationWith(config.roomCode(), config.playerName(), target);
        if (!relation.atWar()) {
            WarStatus status = warClient.refreshReportsAsync(config);
            sendGameMessage(
                "Syx Duo Realm peace",
                "Server relation with " + target + " is " + relation.status() + ".\nRefreshing diplomacy status.\n" + status.message()
            );
            return;
        }

        PeaceRequest outgoing = warClient.outgoingPeaceRequest(config.playerName());
        if (outgoing != null && target.equals(outgoing.toPlayer())) {
            WarStatus status = warClient.refreshReportsAsync(config);
            sendGameMessage(
                "Syx Duo Realm peace",
                "Peace request is already pending for " + target + ".\nRefreshing diplomacy status.\n" + status.message()
            );
            return;
        }

        WarStatus status = warClient.sendPeaceRequestAsync(config, target);
        System.out.println("[Syx Duo Realm] peace request " + status.state() + ": " + status.message());
        if (status.state() == WarStatus.State.FAILED) {
            sendGameMessage("Syx Duo Realm peace failed", status.message() + "\n" + nullToEmpty(status.error()));
            return;
        }

        sendGameMessage(
            "Syx Duo Realm peace",
            "Sent peace request to " + target + ".\nThey must accept before the server relation changes."
        );
    }

    public synchronized ExportStatus lastStatus() {
        return lastStatus;
    }

    public SyncStatus lastSyncStatus() {
        return syncClient.lastStatus();
    }

    public TradeStatus lastTradeStatus() {
        return tradeClient.lastStatus();
    }

    public TradeStatus lastTradeSendStatus() {
        return tradeSendClient.lastStatus();
    }

    public String lastSentTradeId() {
        return tradeSendClient.lastTradeId();
    }

    public RoomStatus lastRoomStatus() {
        return roomClient.lastStatus();
    }

    public RoomState roomState() {
        return roomClient.roomState(config);
    }

    public List<TradePackage> pendingTrades() {
        return tradeClient.inbox().stream()
            .filter(trade -> !claimedTradeStore.contains(trade.tradeId()))
            .toList();
    }

    public int pendingTradeCount() {
        return pendingTrades().size();
    }

    public synchronized TradePackage selectedPendingTrade() {
        List<TradePackage> trades = pendingTrades();
        if (trades.isEmpty()) {
            selectedTradeIndex = 0;
            return null;
        }

        clampSelectedTradeIndex(trades.size());
        return trades.get(selectedTradeIndex);
    }

    public synchronized int selectedTradeIndex() {
        int pending = pendingTradeCount();
        if (pending == 0) {
            selectedTradeIndex = 0;
            return -1;
        }
        clampSelectedTradeIndex(pending);
        return selectedTradeIndex;
    }

    public Path claimedTradePath() {
        return claimedTradeStore.path();
    }

    public Path sentTradePath() {
        return sentTradeStore.path();
    }

    public int pendingSentTradeCount() {
        return sentTradeStore.pendingDeductedCount();
    }

    public SentTradeRecord latestSentTrade() {
        return sentTradeStore.latest();
    }

    public int configuredTradeAvailable() {
        if (!SETT.exists()) {
            return 0;
        }
        RESOURCE resource = configuredTradeResource();
        return resource == null ? 0 : SETT.ROOMS().STOCKPILE.tally().amountReservable.get(resource);
    }

    public String configuredTradeResourceName() {
        RESOURCE resource = configuredTradeResource();
        return resource == null ? "" : resource.name.toString();
    }

    public synchronized void cycleTradeOfferResource() {
        if (!SETT.exists()) {
            sendGameMessage("Syx Duo Realm trade resource", "Settlement is not loaded.");
            return;
        }

        RESOURCE current = configuredTradeResource();
        int currentIndex = current == null ? -1 : current.index();
        RESOURCE selected = null;

        for (int offset = 1; offset <= RESOURCES.ALL().size(); offset++) {
            RESOURCE candidate = RESOURCES.ALL().get((currentIndex + offset + RESOURCES.ALL().size()) % RESOURCES.ALL().size());
            if (SETT.ROOMS().STOCKPILE.tally().amountReservable.get(candidate) > 0) {
                selected = candidate;
                break;
            }
        }

        if (selected == null) {
            selected = RESOURCES.ALL().get((currentIndex + 1 + RESOURCES.ALL().size()) % RESOURCES.ALL().size());
        }

        try {
            config.updateTradeOfferResourceKey(selected.key);
            sendGameMessage(
                "Syx Duo Realm trade resource",
                selected.name + "\nKey: " + selected.key + "\nReservable: "
                    + SETT.ROOMS().STOCKPILE.tally().amountReservable.get(selected)
            );
        } catch (IOException e) {
            sendGameMessage("Syx Duo Realm trade resource failed", "Could not save syx_duo_realm.json.");
            System.err.println("[Syx Duo Realm] Could not save trade resource");
            e.printStackTrace(System.err);
        }
    }

    public synchronized void cycleTradeOfferAmount() {
        int[] amounts = {25, 50, 100, 250, 500, 1000};
        int current = config.tradeOfferAmount();
        int selected = amounts[0];
        for (int amount : amounts) {
            if (amount > current) {
                selected = amount;
                break;
            }
        }

        try {
            config.updateTradeOfferAmount(selected);
            sendGameMessage(
                "Syx Duo Realm trade amount",
                configuredTradeResourceName() + " x" + selected + "\nReservable: " + configuredTradeAvailable()
            );
        } catch (IOException e) {
            sendGameMessage("Syx Duo Realm trade amount failed", "Could not save syx_duo_realm.json.");
            System.err.println("[Syx Duo Realm] Could not save trade amount");
            e.printStackTrace(System.err);
        }
    }

    public String tradeOfferRecipient() {
        String configured = config.tradeOfferToPlayer();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }

        String shadowFriend = shadowFriendPlayerName();
        if (!shadowFriend.isBlank()) {
            return shadowFriend;
        }

        FriendState latest = shadowClient.latestFriendState();
        if (latest != null && latest.playerName() != null && !latest.playerName().isBlank()) {
            return latest.playerName();
        }

        FriendState friend = roomState().friendState();
        if (friend != null && friend.playerName() != null && !friend.playerName().isBlank()) {
            return friend.playerName();
        }

        return "";
    }

    public String friendTargetSource() {
        String configured = config.tradeOfferToPlayer();
        if (configured != null && !configured.isBlank()) {
            return "config";
        }
        if (!shadowFriendPlayerName().isBlank()) {
            return "shadow NPC binding";
        }
        FriendState latest = shadowClient.latestFriendState();
        if (latest != null && latest.playerName() != null && !latest.playerName().isBlank()) {
            return "latest friend_state";
        }
        FriendState friend = roomState().friendState();
        if (friend != null && friend.playerName() != null && !friend.playerName().isBlank()) {
            return "room status";
        }
        return "none";
    }

    public ShadowStatus shadowStatus() {
        ShadowStatus clientStatus = shadowClient.lastStatus();
        if (clientStatus.state() == ShadowStatus.State.IN_FLIGHT || clientStatus.state() == ShadowStatus.State.FAILED) {
            return clientStatus;
        }

        ShadowStatus managerStatus = shadowFactionManager.lastStatus();
        if (managerStatus.state() != ShadowStatus.State.NOT_RUN) {
            return managerStatus;
        }

        return clientStatus;
    }

    public WarStatus warStatus() {
        return warClient.lastStatus();
    }

    public List<WarReport> warReports() {
        return warClient.reports();
    }

    public List<WarRequest> warRequests() {
        return warClient.requests();
    }

    public List<PeaceRequest> peaceRequests() {
        return warClient.peaceRequests();
    }

    public List<DiplomacyRelation> warRelations() {
        return warClient.relations();
    }

    public DiplomacyRelation warRelationWithTarget() {
        String target = tradeOfferRecipient();
        if (target.isBlank()) {
            return null;
        }
        return warClient.relationWith(config.roomCode(), config.playerName(), target);
    }

    public WarRequest incomingWarRequest() {
        return warClient.incomingRequest(config.playerName());
    }

    public WarRequest outgoingWarRequest() {
        return warClient.outgoingRequest(config.playerName());
    }

    public PeaceRequest incomingPeaceRequest() {
        return warClient.incomingPeaceRequest(config.playerName());
    }

    public PeaceRequest outgoingPeaceRequest() {
        return warClient.outgoingPeaceRequest(config.playerName());
    }

    public WarReport latestWarReport() {
        return warClient.latestReport();
    }

    public ShadowBinding shadowBinding() {
        return shadowFactionManager.binding();
    }

    public ShadowFactionView shadowFactionView() {
        return shadowFactionManager.view();
    }

    public FriendState latestFriendState() {
        return shadowClient.latestFriendState();
    }

    public boolean shadowBindingActive() {
        return shadowFactionManager.hasActiveBinding();
    }

    public boolean shadowApplyPending() {
        return shadowApplyRequested;
    }

    public String shadowBindingPath() {
        return shadowFactionManager.bindingPathText();
    }

    public SyxDuoRealmConfig config() {
        return config;
    }

    public String configMessage() {
        return configMessage;
    }

    public String configError() {
        return configError;
    }

    private void installUi() {
        if (uiInstalled) {
            return;
        }

        try {
            SyxDuoRealmPanel panel = new SyxDuoRealmPanel(this);
            uiInjector.injectIntoTopPanels(panel);
            uiInstalled = true;
            System.out.println("[Syx Duo Realm] collapsible UI panel installed");
        } catch (Exception e) {
            lastStatus = ExportStatus.failure(exporter.outputPath(), "Could not install UI button.", e);
            System.err.println("[Syx Duo Realm] Could not install UI button");
            e.printStackTrace(System.err);
        }
    }

    private TradePackage firstPendingTrade() {
        return pendingTrades().stream().findFirst().orElse(null);
    }

    private RESOURCE configuredTradeResource() {
        return RESOURCES.map().tryGet(config.tradeOfferResourceKey());
    }

    private void clampSelectedTradeIndex() {
        clampSelectedTradeIndex(pendingTradeCount());
    }

    private void clampSelectedTradeIndex(int pendingTradeCount) {
        if (pendingTradeCount <= 0) {
            selectedTradeIndex = 0;
        } else if (selectedTradeIndex >= pendingTradeCount) {
            selectedTradeIndex = pendingTradeCount - 1;
        } else if (selectedTradeIndex < 0) {
            selectedTradeIndex = 0;
        }
    }

    private synchronized void processPendingTradeSendResult() {
        PendingSentTrade pending = pendingSentTrade;
        if (pending == null) {
            return;
        }

        TradeStatus status = tradeSendClient.lastStatus();
        if (status.state() == TradeStatus.State.IN_FLIGHT || status.state() == TradeStatus.State.NOT_RUN) {
            return;
        }

        pendingSentTrade = null;
        if (status.state() == TradeStatus.State.SUCCESS) {
            String serverTradeId = tradeSendClient.lastTradeId();
            try {
                sentTradeStore.mark(
                    pending.localTradeId(),
                    SentTradeStore.STATUS_SENT,
                    serverTradeId,
                    "Server accepted outgoing trade."
                );
            } catch (IOException e) {
                sendGameMessage(
                    "Syx Duo Realm trade warning",
                    "Server accepted the trade, but sent_trades.json could not be updated."
                );
                System.err.println("[Syx Duo Realm] Could not mark outgoing trade as sent " + pending.localTradeId());
                e.printStackTrace(System.err);
            }

            sendGameMessage(
                "Syx Duo Realm trade sent",
                pending.resourceKey() + " x" + pending.amount() + " to " + pending.recipient()
                    + "\n" + status.message()
            );
            return;
        }

        refundAndMarkPendingTrade(
            pending,
            "POST /api/trade failed.\n" + status.message() + "\n" + nullToEmpty(status.error())
        );
    }

    private void refundAndMarkPendingTrade(PendingSentTrade pending, String reason) {
        if (pendingSentTrade != null && pendingSentTrade.localTradeId().equals(pending.localTradeId())) {
            pendingSentTrade = null;
        }

        TradeResourceDropper.DropResult refund = refundRemovedResource(
            pending.localTradeId(),
            pending.resourceKey(),
            pending.amount()
        );
        String storeStatus = refund.success() ? SentTradeStore.STATUS_REFUNDED : SentTradeStore.STATUS_REFUND_FAILED;
        String refundNote = refund.success()
            ? "Refund placed near throne."
            : "Refund failed: " + refund.error();

        try {
            sentTradeStore.mark(pending.localTradeId(), storeStatus, null, reason + "\n" + refundNote);
        } catch (IOException e) {
            sendGameMessage(
                "Syx Duo Realm trade warning",
                refundNote + "\nCould not update sent_trades.json."
            );
            System.err.println("[Syx Duo Realm] Could not mark outgoing trade refund " + pending.localTradeId());
            e.printStackTrace(System.err);
        }

        sendGameMessage(
            refund.success() ? "Syx Duo Realm trade refunded" : "Syx Duo Realm trade refund failed",
            reason + "\n" + refundNote
        );
    }

    private TradeResourceDropper.DropResult refundRemovedResource(String localTradeId, String resourceKey, int amount) {
        return tradeDropper.drop(new TradePackage("refund_" + localTradeId, resourceKey, amount, "local-escrow"));
    }

    private String createLocalTradeId(String recipient) {
        return "client_" + safeId(config.playerName()) + "_" + safeId(recipient) + "_" + System.currentTimeMillis();
    }

    private String safeId(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "player" : safe;
    }

    private String shadowFriendPlayerName() {
        ShadowBinding binding = shadowFactionManager.binding();
        if (!shadowFactionManager.hasActiveBinding()) {
            return "";
        }
        String friend = binding.friendPlayerName();
        return friend == null ? "" : friend.trim();
    }

    private synchronized void refreshShadowProjectionIfActive() {
        if (!shadowFactionManager.hasActiveBinding() || shadowApplyRequested) {
            return;
        }

        shadowApplyRequested = true;
        shadowApplyManual = false;
        ShadowStatus status = shadowClient.refreshAsync(config);
        System.out.println("[Syx Duo Realm] shadow refresh " + status.state() + ": " + status.message());
        if (status.state() == ShadowStatus.State.FAILED) {
            shadowApplyRequested = false;
            notifyShadowFailure(status);
        }
    }

    private void applyPendingShadowStateIfReady() {
        if (!shadowApplyRequested) {
            return;
        }

        ShadowStatus status = shadowClient.lastStatus();
        if (status.state() == ShadowStatus.State.IN_FLIGHT || status.state() == ShadowStatus.State.NOT_RUN) {
            return;
        }

        boolean manual = shadowApplyManual;
        shadowApplyRequested = false;
        shadowApplyManual = false;

        if (status.state() == ShadowStatus.State.FAILED) {
            notifyShadowFailure(status);
            return;
        }

        FriendState friendState = shadowClient.latestFriendState();
        if (friendState == null) {
            if (manual) {
                sendGameMessage("Syx Duo Realm shadow", status.message());
            }
            return;
        }

        ShadowFactionManager.ApplyResult result = shadowFactionManager.apply(friendState, saveIdTracker.current());
        lastNativeDiplomacyMirrorKey = "";
        if (manual || !result.success()) {
            sendGameMessage(
                result.success() ? "Syx Duo Realm shadow active" : "Syx Duo Realm shadow failed",
                result.message()
            );
        }
    }

    private synchronized void mirrorNativeDiplomacyIfPossible() {
        if (!config.mirrorNativeDiplomacy() || !shadowFactionManager.hasActiveBinding()) {
            return;
        }

        String target = tradeOfferRecipient();
        if (target.isBlank()) {
            return;
        }

        DiplomacyRelation relation = warClient.relationWith(config.roomCode(), config.playerName(), target);
        String mirrorKey = shadowFactionManager.binding().friendFactionIndex()
            + ":" + relation.relationId()
            + ":" + relation.status()
            + ":" + relation.updatedAt()
            + ":" + config.nativePeaceStance();
        if (mirrorKey.equals(lastNativeDiplomacyMirrorKey)) {
            return;
        }

        ShadowFactionManager.MirrorResult result = shadowFactionManager.mirrorDiplomacy(
            relation,
            config.playerName(),
            target,
            config.mirrorNativeDiplomacy(),
            config.nativePeaceStance()
        );
        if (result.success() || result.skipped()) {
            lastNativeDiplomacyMirrorKey = mirrorKey;
        }
        if (!result.success() && !result.skipped()) {
            System.err.println("[Syx Duo Realm] native diplomacy mirror failed: " + result.message());
        }
    }

    private void publishSyncNotificationIfNeeded() {
        SyncStatus status = syncClient.lastStatus();

        if (status.state() == SyncStatus.State.FAILED) {
            String key = status.notificationKey();
            if (!key.equals(lastSyncNotificationKey)) {
                lastSyncNotificationKey = key;
                lastNotifiedSyncWasFailure = true;
                sendGameMessage(
                    "Syx Duo Realm sync failed",
                    status.message() + "\n" + nullToEmpty(status.error()) + "\n" + status.serverUrl()
                );
            }
            return;
        }

        if (status.state() == SyncStatus.State.SUCCESS && lastNotifiedSyncWasFailure) {
            lastSyncNotificationKey = status.notificationKey();
            lastNotifiedSyncWasFailure = false;
            sendGameMessage(
                "Syx Duo Realm sync restored",
                "Latest city state reached " + status.serverUrl() + "."
            );
        }
    }

    private void notifyShadowFailure(ShadowStatus status) {
        String key = status.notificationKey();
        if (key.equals(lastShadowNotificationKey)) {
            return;
        }
        lastShadowNotificationKey = key;
        sendGameMessage(
            "Syx Duo Realm shadow failed",
            status.message() + "\n" + nullToEmpty(status.error())
        );
    }

    private void sendGameMessage(String title, String body) {
        try {
            if (!VIEW.existTemp()) {
                return;
            }
            GBox box = VIEW.timeBox();
            box.title(title);
            for (String line : body.split("\\R")) {
                if (!line.isBlank()) {
                    box.text(line);
                    box.NL();
                }
            }
        } catch (Throwable throwable) {
            System.err.println("[Syx Duo Realm] Could not show game message: " + title);
            throwable.printStackTrace(System.err);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record PendingSentTrade(String localTradeId, String recipient, String resourceKey, int amount) {
    }
}
