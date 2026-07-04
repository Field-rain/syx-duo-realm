package com.syxduorealm.export;

import game.faction.FACTIONS;
import game.faction.Faction;
import init.race.RACES;
import init.race.Race;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.main.SETT;
import settlement.stats.POP;
import world.WORLD;
import world.army.AD;
import world.map.regions.Region;
import world.region.RD;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CityStateCollector {

    public CityState collect(String saveId, String playerName, String roomCode) {
        if (!SETT.exists()) {
            throw new IllegalStateException("Settlement is not loaded.");
        }

        Faction player = FACTIONS.player();
        Integer seed = worldSeed();
        SaveIdentity identity = SaveIdentity.resolve(saveId, playerName, roomCode, seed);

        return new CityState(
            identity.id(),
            identity.source(),
            seed,
            text(player.name),
            POP.tot(null, null),
            populationByRace(),
            resources(),
            militaryProfile(player),
            FACTIONS.NPCs().size(),
            player.realm().regions(),
            Instant.now().toString()
        );
    }

    private Integer worldSeed() {
        try {
            return WORLD.GEN().seed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Map<String, CityState.RacePopulation> populationByRace() {
        Map<String, CityState.RacePopulation> out = new LinkedHashMap<>();
        for (Race race : RACES.all()) {
            out.put(race.key, new CityState.RacePopulation(text(race.info.name), POP.tot(null, race)));
        }
        return out;
    }

    private Map<String, CityState.ResourceAmount> resources() {
        Map<String, CityState.ResourceAmount> out = new LinkedHashMap<>();
        for (RESOURCE resource : RESOURCES.ALL()) {
            int amount = SETT.ROOMS().STOCKPILE.tally().amountTotal(resource);
            out.put(resource.key, new CityState.ResourceAmount(text(resource.name), amount));
        }
        return out;
    }

    private CityState.MilitaryProfile militaryProfile(Faction player) {
        double offensivePower = safeDouble(player::offensivePower);
        int fieldArmyPower = safeInt(() -> AD.power().get(player));
        int armyCount = safeInt(() -> player.armies().all().size());
        double capitalMilitaryPower = safeDouble(() -> {
            Region capitol = player.capitolRegion();
            return capitol == null ? 0 : RD.MILITARY().power.getD(capitol);
        });
        int realmRegions = safeInt(() -> player.realm().regions());

        return new CityState.MilitaryProfile(
            offensivePower,
            fieldArmyPower,
            armyCount,
            capitalMilitaryPower,
            realmRegions,
            "FACTIONS.player().offensivePower + AD.power + RD.MILITARY"
        );
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

    private String text(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    private interface DoubleSupplier {
        double getAsDouble();
    }

    private interface IntSupplier {
        int getAsInt();
    }
}
