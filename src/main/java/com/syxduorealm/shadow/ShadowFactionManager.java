package com.syxduorealm.shadow;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.npc.FactionNPC;
import init.race.RACES;
import init.race.Race;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.trade.TR;
import com.syxduorealm.war.DiplomacyRelation;
import world.WORLD;
import world.map.regions.Region;
import world.region.RD;
import world.region.pop.RDRace;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

public final class ShadowFactionManager {

    private static final int MAX_FACTION_NAME_LENGTH = 23;

    private final ShadowBindingStore bindingStore;
    private ShadowStatus lastStatus = ShadowStatus.notRun();
    private String lastMirroredServerRelation = "";
    private String lastMirroredNativeStance = "";
    private String lastMirroredAt = "";

    public ShadowFactionManager(ShadowBindingStore bindingStore) {
        this.bindingStore = bindingStore;
    }

    public synchronized ApplyResult apply(FriendState friendState, String localSaveId) {
        if (friendState == null) {
            lastStatus = ShadowStatus.failure("Could not apply shadow faction.", "No friend_state available.");
            return ApplyResult.failure(lastStatus.message());
        }

        ShadowBinding existing = bindingStore.binding();
        FactionNPC existingFaction = boundFaction(existing);
        if (existing.enabled() && existingFaction != null) {
            try {
                Region existingRegion = WORLD.REGIONS().getByIndex(existing.regionIndex());
                projectFriendState(existingFaction, existingRegion, friendState);
                bindingStore.save(ShadowBinding.active(existingFaction.index(), existingRegion.index(), friendState, localSaveId));
                lastStatus = ShadowStatus.success("Shadow faction refreshed: " + displayName(friendState));
                return ApplyResult.success(displayName(friendState), existing.friendFactionIndex(), existing.regionIndex());
            } catch (Throwable throwable) {
                lastStatus = ShadowStatus.failure("Could not refresh shadow faction.", throwable);
                System.err.println("[Syx Duo Realm] Could not refresh shadow faction");
                throwable.printStackTrace(System.err);
                return ApplyResult.failure(lastStatus.error());
            }
        }

        if (!FACTIONS.canActivateNext()) {
            lastStatus = ShadowStatus.failure("Could not apply shadow faction.", "No inactive NPC faction slot is available.");
            return ApplyResult.failure(lastStatus.error());
        }

        Region region = findEmptyRegion();
        if (region == null) {
            lastStatus = ShadowStatus.failure("Could not apply shadow faction.", "No empty active world region is available.");
            return ApplyResult.failure(lastStatus.error());
        }

        try {
            FactionNPC faction = FACTIONS.activateNext(region, preferredRace(friendState), false);
            if (faction == null) {
                lastStatus = ShadowStatus.failure("Could not apply shadow faction.", "FACTIONS.activateNext returned no faction.");
                return ApplyResult.failure(lastStatus.error());
            }

            projectFriendState(faction, region, friendState);

            ShadowBinding binding = ShadowBinding.active(faction.index(), region.index(), friendState, localSaveId);
            bindingStore.save(binding);

            String displayName = displayName(friendState);
            lastStatus = ShadowStatus.success("Shadow faction active: " + displayName);
            return ApplyResult.success(displayName, faction.index(), region.index());
        } catch (Throwable throwable) {
            lastStatus = ShadowStatus.failure("Could not apply shadow faction.", throwable);
            System.err.println("[Syx Duo Realm] Could not apply shadow faction");
            throwable.printStackTrace(System.err);
            return ApplyResult.failure(lastStatus.error());
        }
    }

    public synchronized CleanupResult cleanup() {
        ShadowBinding binding = bindingStore.binding();
        if (!binding.enabled()) {
            lastStatus = ShadowStatus.success("No shadow faction binding is active.");
            return CleanupResult.success("No shadow faction binding is active.");
        }

        FactionNPC faction = boundFaction(binding);
        if (faction != null) {
            try {
                FACTIONS.remove(faction, false);
            } catch (Throwable throwable) {
                lastStatus = ShadowStatus.failure("Could not remove shadow faction.", throwable);
                System.err.println("[Syx Duo Realm] Could not remove shadow faction");
                throwable.printStackTrace(System.err);
                return CleanupResult.failure(lastStatus.error());
            }
        }

        try {
            bindingStore.clear();
            String message = faction == null
                ? "Cleared stale shadow binding; no matching active faction was found."
                : "Removed shadow faction: " + binding.friendPlayerName();
            lastStatus = ShadowStatus.success(message);
            return CleanupResult.success(message);
        } catch (IOException e) {
            lastStatus = ShadowStatus.failure("Shadow faction was removed, but binding file could not be cleared.", e);
            System.err.println("[Syx Duo Realm] Could not clear shadow binding");
            e.printStackTrace(System.err);
            return CleanupResult.failure(lastStatus.error());
        }
    }

    public synchronized MirrorResult mirrorDiplomacy(
        DiplomacyRelation relation,
        String localPlayerName,
        String friendPlayerName,
        boolean enabled,
        String peaceStanceKey
    ) {
        if (!enabled) {
            return MirrorResult.skipped("Native diplomacy mirror is disabled.");
        }

        ShadowBinding binding = bindingStore.binding();
        FactionNPC faction = boundFaction(binding);
        if (faction == null) {
            return MirrorResult.skipped("No active shadow faction to mirror.");
        }
        if (relation == null) {
            return MirrorResult.skipped("No server diplomacy relation to mirror.");
        }
        if (!relation.involvesPair(localPlayerName, friendPlayerName)) {
            return MirrorResult.skipped("Server relation does not match the bound friend.");
        }

        DipStance target = nativeStanceFor(relation.status(), peaceStanceKey);
        DipStance current = DIP.get(faction);
        try {
            if (current != target) {
                target.set(faction);
            }
            lastMirroredServerRelation = relation.status();
            lastMirroredNativeStance = target.key();
            lastMirroredAt = Instant.now().toString();
            lastStatus = ShadowStatus.success(
                "Native diplomacy mirrored: " + relation.status() + " -> " + target.key()
            );
            return MirrorResult.success(relation.status(), target.key(), lastMirroredAt);
        } catch (Throwable throwable) {
            lastStatus = ShadowStatus.failure("Could not mirror native diplomacy.", throwable);
            System.err.println("[Syx Duo Realm] Could not mirror native diplomacy");
            throwable.printStackTrace(System.err);
            return MirrorResult.failure(lastStatus.error());
        }
    }

    public synchronized ShadowStatus lastStatus() {
        return lastStatus;
    }

    public ShadowBinding binding() {
        return bindingStore.binding();
    }

    public synchronized ShadowFactionView view() {
        ShadowBinding binding = bindingStore.binding();
        FactionNPC faction = boundFaction(binding);
        if (faction == null) {
            return ShadowFactionView.none();
        }

        try {
            Region region = WORLD.REGIONS().getByIndex(binding.regionIndex());
            return new ShadowFactionView(
                true,
                faction.index(),
                region.index(),
                text(faction.name),
                text(region.info.name()),
                text(DIP.get(faction).name),
                DIP.get(faction).key(),
                text(DIP.get(faction).name),
                lastMirroredServerRelation,
                lastMirroredNativeStance,
                lastMirroredAt,
                safeDouble(faction::offensivePower),
                safeDouble(() -> RD.MILITARY().power.getD(region)),
                safeInt(() -> RD.RACES().population.get(region))
            );
        } catch (Throwable throwable) {
            return ShadowFactionView.none();
        }
    }

    public boolean hasActiveBinding() {
        ShadowBinding binding = bindingStore.binding();
        return binding.enabled() && boundFaction(binding) != null;
    }

    public String bindingPathText() {
        return bindingStore.path().toString();
    }

    private FactionNPC boundFaction(ShadowBinding binding) {
        if (!binding.enabled() || binding.friendFactionIndex() < 0 || binding.regionIndex() < 0) {
            return null;
        }

        try {
            Faction faction = FACTIONS.getByIndex(binding.friendFactionIndex());
            if (!(faction instanceof FactionNPC npc) || !npc.isActive()) {
                return null;
            }

            Region region = WORLD.REGIONS().getByIndex(binding.regionIndex());
            if (region.realm() == null || region.faction() != npc) {
                return null;
            }

            return npc;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Region findEmptyRegion() {
        for (Region region : WORLD.REGIONS().active()) {
            if (region == WORLD.REGIONS().player) {
                continue;
            }
            if (!region.active()) {
                continue;
            }
            if (region.realm() == null) {
                return region;
            }
        }
        return null;
    }

    private void projectFriendState(FactionNPC faction, Region region, FriendState friendState) {
        String displayName = displayName(friendState);
        faction.name.clear().add(displayName);
        faction.eventSet(true);
        region.info.name().clear().add(displayName);

        projectPopulation(region, friendState);
        faction.stockpile.update(faction, 0);
        projectResources(faction, friendState);
    }

    private DipStance nativeStanceFor(String serverStatus, String peaceStanceKey) {
        String status = serverStatus == null ? "" : serverStatus.trim().toUpperCase();
        if ("WAR".equals(status)) {
            return DIP.WAR();
        }
        if ("TRUCE".equals(status)) {
            return DIP.NEUTRAL();
        }

        String peaceStance = peaceStanceKey == null ? "" : peaceStanceKey.trim().toUpperCase();
        return switch (peaceStance) {
            case "NEUTRAL" -> DIP.NEUTRAL();
            case "PACT" -> DIP.PACT();
            case "ALLY" -> DIP.ALLY();
            default -> DIP.TRADE();
        };
    }

    private void projectPopulation(Region region, FriendState friendState) {
        if (region == null) {
            return;
        }

        for (Race race : RACES.all()) {
            RDRace rdRace = RD.RACES().get(race);
            if (rdRace != null) {
                rdRace.pop.set(region, 0);
            }
        }

        int assigned = 0;
        Map<String, FriendState.RacePopulation> populations = friendState.populationByRace();
        if (populations != null && !populations.isEmpty()) {
            for (Race race : RACES.all()) {
                FriendState.RacePopulation population = populations.get(race.key);
                if (population == null || population.count() <= 0) {
                    continue;
                }

                RDRace rdRace = RD.RACES().get(race);
                if (rdRace == null) {
                    continue;
                }

                int count = clamp(population.count(), 0, rdRace.pop.max(region));
                rdRace.pop.set(region, count);
                assigned += count;
            }
        }

        if (assigned == 0 && friendState.populationTotal() != null && friendState.populationTotal() > 0) {
            RDRace race = preferredRace(friendState);
            if (race != null) {
                race.pop.set(region, clamp(friendState.populationTotal(), 0, race.pop.max(region)));
            }
        }
    }

    private void projectResources(FactionNPC faction, FriendState friendState) {
        Map<String, FriendState.ResourceAmount> resources = friendState.resources();
        if (resources == null || resources.isEmpty()) {
            return;
        }

        for (RESOURCE resource : RESOURCES.ALL()) {
            FriendState.ResourceAmount amount = resources.get(resource.key);
            int targetAmount = amount == null ? 0 : Math.max(0, amount.amount());
            double delta = targetAmount - faction.res(TR.get(resource)).amount();
            faction.res(TR.get(resource)).inc(delta);
        }
    }

    private RDRace preferredRace(FriendState friendState) {
        String dominantRace = friendState == null ? "" : friendState.dominantRace();
        if (dominantRace != null && !dominantRace.isBlank()) {
            for (Race race : RACES.all()) {
                if (dominantRace.equals(race.key)) {
                    RDRace rdRace = RD.RACES().get(race);
                    if (rdRace != null) {
                        return rdRace;
                    }
                }
            }
        }

        int bestCount = -1;
        RDRace bestRace = null;
        Map<String, FriendState.RacePopulation> populations = friendState == null ? null : friendState.populationByRace();
        if (populations != null) {
            for (Race race : RACES.all()) {
                FriendState.RacePopulation population = populations.get(race.key);
                if (population != null && population.count() > bestCount) {
                    RDRace rdRace = RD.RACES().get(race);
                    if (rdRace != null) {
                        bestCount = population.count();
                        bestRace = rdRace;
                    }
                }
            }
        }

        if (bestRace != null) {
            return bestRace;
        }

        return RD.RACES().all.size() == 0 ? null : RD.RACES().all.get(0);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String displayName(FriendState friendState) {
        String name = friendState.playerName();
        if (name == null || name.isBlank()) {
            name = friendState.playerFactionName();
        }
        if (name == null || name.isBlank()) {
            name = "Duo Friend";
        }
        name = name.trim();
        if (name.length() <= MAX_FACTION_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, MAX_FACTION_NAME_LENGTH);
    }

    private double safeDouble(DoubleSupplier supplier) {
        try {
            return supplier.getAsDouble();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int safeInt(IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private interface DoubleSupplier {
        double getAsDouble();
    }

    private interface IntSupplier {
        int getAsInt();
    }

    public record ApplyResult(boolean success, String message, String friendName, int factionIndex, int regionIndex) {
        public static ApplyResult success(String friendName, int factionIndex, int regionIndex) {
            return new ApplyResult(true, "Shadow faction active: " + friendName, friendName, factionIndex, regionIndex);
        }

        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message == null ? "Shadow faction failed." : message, "", -1, -1);
        }
    }

    public record CleanupResult(boolean success, String message) {
        public static CleanupResult success(String message) {
            return new CleanupResult(true, message);
        }

        public static CleanupResult failure(String message) {
            return new CleanupResult(false, message == null ? "Shadow faction cleanup failed." : message);
        }
    }

    public record MirrorResult(boolean success, boolean skipped, String message, String serverStatus, String nativeStance, String mirroredAt) {
        public static MirrorResult success(String serverStatus, String nativeStance, String mirroredAt) {
            return new MirrorResult(
                true,
                false,
                "Native diplomacy mirrored: " + serverStatus + " -> " + nativeStance,
                serverStatus,
                nativeStance,
                mirroredAt
            );
        }

        public static MirrorResult skipped(String message) {
            return new MirrorResult(false, true, message == null ? "Native diplomacy mirror skipped." : message, "", "", "");
        }

        public static MirrorResult failure(String message) {
            return new MirrorResult(false, false, message == null ? "Native diplomacy mirror failed." : message, "", "", "");
        }
    }
}
