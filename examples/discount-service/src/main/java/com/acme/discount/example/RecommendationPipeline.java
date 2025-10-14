package com.acme.discount.example;

import com.acme.discount.CustomerProfile;
import com.acme.discount.Order;
import com.acme.discount.ProductCategory;
import com.acme.discount.SeasonalPromotion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class RecommendationPipeline {
    private final SeasonalPromotion promotion;

    public RecommendationPipeline(SeasonalPromotion promotion) {
        this.promotion = Objects.requireNonNull(promotion, "promotion");
    }

    public Context prepare(Order order, CustomerProfile profile, LocalDate date) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(date, "date");

        Set<ProductCategory> categories = EnumSet.noneOf(ProductCategory.class);
        order.getLines().forEach(line -> categories.add(line.getCategory()));
        boolean promotionActive = promotion.isActive(date);
        return new Context(order, profile, promotionActive, categories);
    }

    public Plan generatePlan(Context context) {
        Objects.requireNonNull(context, "context");
        List<String> targets = new ArrayList<>();
        for (ProductCategory category : context.categories()) {
            if (promotion.appliesTo(category)) {
                targets.add(category.name());
            }
        }
        if (targets.isEmpty()) {
            targets.add("DEFAULT_UPSELL");
        }
        double lift = context.promotionActive() ? 1.5 : 1.1;
        return new Plan(List.copyOf(targets), lift);
    }

    public record Context(Order order,
                          CustomerProfile profile,
                          boolean promotionActive,
                          Set<ProductCategory> categories) {
        public Context {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(profile, "profile");
            categories = Set.copyOf(categories);
        }
    }

    public record Plan(List<String> targetCategories, double expectedRevenueLift) {
        public Plan {
            targetCategories = List.copyOf(targetCategories);
        }
    }
}
