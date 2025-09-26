package com.acme.discount;

public final class OrderLine {
    private final String sku;
    private final ProductCategory category;
    private final int quantity;
    private final double unitPrice;

    public OrderLine(String sku, ProductCategory category, int quantity, double unitPrice) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU must be provided");
        }
        if (category == null) {
            throw new IllegalArgumentException("Category must be provided");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("Unit price must be non-negative");
        }
        this.sku = sku;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getSku() {
        return sku;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getLineTotal() {
        return Math.round(quantity * unitPrice * 100.0) / 100.0;
    }
}
