package com.acme.discount.complex;

import com.acme.discount.CustomerProfile;
import com.acme.discount.DiscountOutcome;

import java.util.Objects;

public class LoyaltyLedger {

    public LoyaltySnapshot recordAccrual(CustomerProfile profile, DiscountOutcome outcome) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(outcome, "outcome");
        int basePoints = switch (profile.getLoyaltyTier()) {
            case BRONZE -> 10;
            case SILVER -> 20;
            case GOLD -> 30;
            case PLATINUM -> 50;
            default -> 5;
        };
        int multiplier = outcome.applied() ? 2 : 1;
        int awarded = basePoints * multiplier;
        return new LoyaltySnapshot(profile.getCustomerId(), awarded);
    }

    public record LoyaltySnapshot(String customerId, int pointsAwarded) {
        public LoyaltySnapshot {
            Objects.requireNonNull(customerId, "customerId");
            pointsAwarded = Math.max(0, pointsAwarded);
        }
    }
}
