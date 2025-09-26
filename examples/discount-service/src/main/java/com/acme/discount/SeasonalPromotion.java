package com.acme.discount;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;
import java.util.Set;

public final class SeasonalPromotion {
    private final String name;
    private final Month startMonth;
    private final Month endMonth;
    private final Set<ProductCategory> categories;
    private final double percentage;

    public SeasonalPromotion(String name, Month startMonth, Month endMonth, Set<ProductCategory> categories, double percentage) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Promotion name must be provided");
        }
        this.name = name;
        this.startMonth = Objects.requireNonNull(startMonth, "Start month must be provided");
        this.endMonth = Objects.requireNonNull(endMonth, "End month must be provided");
        this.categories = categories == null || categories.isEmpty() ? Set.of() : Set.copyOf(categories);
        if (percentage <= 0) {
            throw new IllegalArgumentException("Percentage must be positive");
        }
        this.percentage = percentage;
    }

    public String getName() {
        return name;
    }

    public double getPercentage() {
        return percentage;
    }

    public boolean isActive(LocalDate date) {
        Month month = Objects.requireNonNull(date, "Date must be provided").getMonth();
        if (startMonth.ordinal() <= endMonth.ordinal()) {
            return month.ordinal() >= startMonth.ordinal() && month.ordinal() <= endMonth.ordinal();
        }
        return month.ordinal() >= startMonth.ordinal() || month.ordinal() <= endMonth.ordinal();
    }

    public boolean appliesTo(ProductCategory category) {
        return categories.isEmpty() || categories.contains(category);
    }
}
