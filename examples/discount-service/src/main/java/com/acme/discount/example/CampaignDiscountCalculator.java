package com.acme.discount.example;

import com.acme.discount.CustomerProfile;
import com.acme.discount.DiscountEngine;
import com.acme.discount.DiscountOutcome;
import com.acme.discount.DiscountResult;
import com.acme.discount.Order;
import com.acme.discount.SeasonalPromotion;

import java.time.LocalDate;
import java.util.Objects;

public class CampaignDiscountCalculator {
    private final DiscountEngine discountEngine;

    public CampaignDiscountCalculator(DiscountEngine discountEngine) {
        this.discountEngine = Objects.requireNonNull(discountEngine, "discountEngine");
    }

    public Result calculate(Order order,
                            CustomerProfile profile,
                            SeasonalPromotion promotion,
                            RecommendationPipeline.Plan plan,
                            LocalDate calculationDate) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(promotion, "promotion");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(calculationDate, "calculationDate");

        DiscountResult result = discountEngine.calculate(order, profile, calculationDate);
        double totalDiscount = result.getTotalDiscount();
        String reason = "Campaign %s targeting %s".formatted(promotion.getName(), plan.targetCategories());
        DiscountOutcome outcome = totalDiscount > 0
                ? DiscountOutcome.applied(totalDiscount, reason)
                : DiscountOutcome.skipped("No campaign discount computed");
        return new Result(outcome, result);
    }

    public record Result(DiscountOutcome outcome, DiscountResult result) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(result, "result");
        }
    }
}
