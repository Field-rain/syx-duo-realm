package com.syxduorealm.trade;

import init.resources.RESOURCE;
import settlement.main.SETT;

public final class TradeEscrowService {

    public EscrowResult deduct(RESOURCE resource, int amount) {
        if (!SETT.exists()) {
            return EscrowResult.failure("Settlement is not loaded.", 0);
        }
        if (resource == null) {
            return EscrowResult.failure("Resource is not resolved.", 0);
        }
        if (amount <= 0) {
            return EscrowResult.failure("Amount must be positive.", 0);
        }

        int available = SETT.ROOMS().STOCKPILE.tally().amountReservable.get(resource);
        if (available < amount) {
            return EscrowResult.failure("Reservable stockpile amount is too low: " + available + " < " + amount, 0);
        }

        int removed = resource.remove(amount, null);
        if (removed == amount) {
            return EscrowResult.success(removed);
        }
        return EscrowResult.failure("Only removed " + removed + " of " + amount + ".", removed);
    }

    public record EscrowResult(boolean success, String error, int removed) {
        public static EscrowResult success(int removed) {
            return new EscrowResult(true, null, removed);
        }

        public static EscrowResult failure(String error, int removed) {
            return new EscrowResult(false, error, removed);
        }
    }
}
