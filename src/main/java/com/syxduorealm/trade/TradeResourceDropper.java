package com.syxduorealm.trade;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.main.SETT;
import settlement.room.main.throne.THRONE;
import settlement.thing.ThingsResources;
import snake2d.util.datatypes.COORDINATE;

public final class TradeResourceDropper {

    public DropResult drop(TradePackage trade) {
        if (!SETT.exists()) {
            return DropResult.failure("Settlement is not loaded.");
        }

        RESOURCE resource = RESOURCES.map().tryGet(trade.resourceKey());
        if (resource == null) {
            return DropResult.failure("Unknown resource key: " + trade.resourceKey());
        }

        if (trade.amount() <= 0) {
            return DropResult.failure("Trade amount must be positive.");
        }

        int remaining = trade.amount();
        int placed = 0;
        int offsetIndex = 0;

        while (remaining > 0) {
            Tile tile = tileNearThrone(offsetIndex++);
            if (tile == null) {
                return DropResult.failure("Could not find an in-bounds tile near the throne.");
            }

            int chunk = Math.min(remaining, ThingsResources.MAX_AMOUNT);
            SETT.THINGS().resources.createPrecise(tile.x(), tile.y(), resource, chunk);
            remaining -= chunk;
            placed += chunk;
        }

        return DropResult.success(resource.key, resource.name.toString(), placed);
    }

    private Tile tileNearThrone(int index) {
        COORDINATE throne = THRONE.coo();
        int cx = throne == null ? SETT.TWIDTH / 2 : throne.x();
        int cy = throne == null ? SETT.THEIGHT / 2 : throne.y();

        int seen = 0;
        for (int radius = 1; radius <= 8; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }
                    int tx = cx + dx;
                    int ty = cy + dy;
                    if (!SETT.IN_BOUNDS(tx, ty)) {
                        continue;
                    }
                    if (seen == index) {
                        return new Tile(tx, ty);
                    }
                    seen++;
                }
            }
        }
        return SETT.IN_BOUNDS(cx, cy) ? new Tile(cx, cy) : null;
    }

    private record Tile(int x, int y) {
    }

    public static final class DropResult {
        private final boolean success;
        private final String resourceKey;
        private final String resourceName;
        private final int amount;
        private final String error;

        private DropResult(boolean success, String resourceKey, String resourceName, int amount, String error) {
            this.success = success;
            this.resourceKey = resourceKey;
            this.resourceName = resourceName;
            this.amount = amount;
            this.error = error;
        }

        public static DropResult success(String resourceKey, String resourceName, int amount) {
            return new DropResult(true, resourceKey, resourceName, amount, null);
        }

        public static DropResult failure(String error) {
            return new DropResult(false, null, null, 0, error);
        }

        public boolean success() {
            return success;
        }

        public String resourceKey() {
            return resourceKey;
        }

        public String resourceName() {
            return resourceName;
        }

        public int amount() {
            return amount;
        }

        public String error() {
            return error;
        }
    }
}
