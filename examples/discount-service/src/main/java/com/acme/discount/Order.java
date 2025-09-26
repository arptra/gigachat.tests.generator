package com.acme.discount;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Order {
    private final String orderId;
    private final LocalDate orderDate;
    private final List<OrderLine> lines;

    public Order(String orderId, LocalDate orderDate, List<OrderLine> lines) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order id must be provided");
        }
        if (orderDate == null) {
            throw new IllegalArgumentException("Order date must be provided");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one line");
        }
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.lines = List.copyOf(lines);
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public double getSubtotal() {
        return round(lines.stream().mapToDouble(OrderLine::getLineTotal).sum());
    }

    public double getSubtotalForCategory(ProductCategory category) {
        return round(lines.stream()
                .filter(line -> line.getCategory() == category)
                .mapToDouble(OrderLine::getLineTotal)
                .sum());
    }

    public int getQuantityForCategory(ProductCategory category) {
        return lines.stream()
                .filter(line -> line.getCategory() == category)
                .mapToInt(OrderLine::getQuantity)
                .sum();
    }

    public Optional<ProductCategory> getDominantCategory() {
        Map<ProductCategory, Double> totalsByCategory = lines.stream()
                .collect(Collectors.groupingBy(OrderLine::getCategory,
                        Collectors.summingDouble(OrderLine::getLineTotal)));
        return totalsByCategory.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
