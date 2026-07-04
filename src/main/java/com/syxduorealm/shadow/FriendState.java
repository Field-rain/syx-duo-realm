package com.syxduorealm.shadow;

import java.util.LinkedHashMap;
import java.util.Map;

public record FriendState(
    String playerName,
    String saveId,
    String saveIdSource,
    Integer worldSeed,
    String playerFactionName,
    Integer populationTotal,
    Map<String, RacePopulation> populationByRace,
    String dominantRace,
    Map<String, ResourceAmount> resources,
    Integer activeNpcFactionsCount,
    Integer playerRealmRegionsCount,
    MilitaryProfile militaryProfile,
    String receivedAt,
    Integer secondsSinceReceived,
    Boolean fresh,
    Integer staleAfterSeconds,
    String sentAt
) {

    public Map<String, Object> toJsonMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("playerName", playerName);
        out.put("saveId", saveId);
        out.put("saveIdSource", saveIdSource);
        out.put("worldSeed", worldSeed);
        out.put("playerFactionName", playerFactionName);
        out.put("populationTotal", populationTotal);
        out.put("populationByRace", populationByRace);
        out.put("dominantRace", dominantRace);
        out.put("resources", resources);
        out.put("activeNpcFactionsCount", activeNpcFactionsCount);
        out.put("playerRealmRegionsCount", playerRealmRegionsCount);
        out.put("militaryProfile", militaryProfile == null ? null : militaryProfile.toJsonMap());
        out.put("receivedAt", receivedAt);
        out.put("secondsSinceReceived", secondsSinceReceived);
        out.put("fresh", fresh);
        out.put("staleAfterSeconds", staleAfterSeconds);
        out.put("sentAt", sentAt);
        return out;
    }

    public record RacePopulation(String name, int count) {
        public Map<String, Object> toJsonMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("count", count);
            return out;
        }
    }

    public record ResourceAmount(String name, int amount) {
        public Map<String, Object> toJsonMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("amount", amount);
            return out;
        }
    }

    public record MilitaryProfile(
        Double offensivePower,
        Integer fieldArmyPower,
        Integer armyCount,
        Double capitalMilitaryPower,
        Integer realmRegions,
        String source
    ) {
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
