package com.syxduorealm.export;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CityState {

    private final String saveId;
    private final String saveIdSource;
    private final Integer worldSeed;
    private final String playerFactionName;
    private final int populationTotal;
    private final Map<String, RacePopulation> populationByRace;
    private final Map<String, ResourceAmount> resources;
    private final MilitaryProfile militaryProfile;
    private final int activeNpcFactionsCount;
    private final int playerRealmRegionsCount;
    private final String timestamp;

    public CityState(
        String saveId,
        String saveIdSource,
        Integer worldSeed,
        String playerFactionName,
        int populationTotal,
        Map<String, RacePopulation> populationByRace,
        Map<String, ResourceAmount> resources,
        MilitaryProfile militaryProfile,
        int activeNpcFactionsCount,
        int playerRealmRegionsCount,
        String timestamp
    ) {
        this.saveId = saveId;
        this.saveIdSource = saveIdSource;
        this.worldSeed = worldSeed;
        this.playerFactionName = playerFactionName;
        this.populationTotal = populationTotal;
        this.populationByRace = populationByRace;
        this.resources = resources;
        this.militaryProfile = militaryProfile;
        this.activeNpcFactionsCount = activeNpcFactionsCount;
        this.playerRealmRegionsCount = playerRealmRegionsCount;
        this.timestamp = timestamp;
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saveId", saveId);
        out.put("saveIdSource", saveIdSource);
        out.put("worldSeed", worldSeed);
        out.put("playerFactionName", playerFactionName);
        out.put("populationTotal", populationTotal);
        out.put("populationByRace", populationByRace);
        out.put("resources", resources);
        out.put("militaryProfile", militaryProfile == null ? null : militaryProfile.toJsonMap());
        out.put("activeNpcFactionsCount", activeNpcFactionsCount);
        out.put("playerRealmRegionsCount", playerRealmRegionsCount);
        out.put("timestamp", timestamp);
        return out;
    }

    public static final class RacePopulation {
        private final String name;
        private final int count;

        public RacePopulation(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public Map<String, Object> toJsonMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("count", count);
            return out;
        }
    }

    public static final class ResourceAmount {
        private final String name;
        private final int amount;

        public ResourceAmount(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }

        public Map<String, Object> toJsonMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("amount", amount);
            return out;
        }
    }

    public static final class MilitaryProfile {
        private final double offensivePower;
        private final int fieldArmyPower;
        private final int armyCount;
        private final double capitalMilitaryPower;
        private final int realmRegions;
        private final String source;

        public MilitaryProfile(
            double offensivePower,
            int fieldArmyPower,
            int armyCount,
            double capitalMilitaryPower,
            int realmRegions,
            String source
        ) {
            this.offensivePower = offensivePower;
            this.fieldArmyPower = fieldArmyPower;
            this.armyCount = armyCount;
            this.capitalMilitaryPower = capitalMilitaryPower;
            this.realmRegions = realmRegions;
            this.source = source;
        }

        public Map<String, Object> toJsonMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("offensivePower", offensivePower);
            out.put("fieldArmyPower", fieldArmyPower);
            out.put("armyCount", armyCount);
            out.put("capitalMilitaryPower", capitalMilitaryPower);
            out.put("realmRegions", realmRegions);
            out.put("source", source);
            return out;
        }
    }
}
