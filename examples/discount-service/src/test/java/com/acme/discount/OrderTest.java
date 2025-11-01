package com.acme.discount;

import static java.lang.Double.compare;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void testConstructorWithValidArguments() {
        String orderId = "1234";
        LocalDate orderDate = LocalDate.of(2023, 10, 1);
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine("SKU1", ProductCategory.ELECTRONICS, 2, 100.0));
        lines.add(new OrderLine("SKU2", ProductCategory.FASHION, 1, 50.0));

        Order order = new Order(orderId, orderDate, lines);

        assertEquals(orderId, order.getOrderId());
        assertEquals(orderDate, order.getOrderDate());
        assertEquals(lines, order.getLines());
    }

    @Test
    void constructorShouldSetFieldsCorrectly() {
        String orderId = "1234";
        LocalDate orderDate = LocalDate.of(2023, 10, 1);
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine("SKU3", ProductCategory.GROCERY, 3, 25.0));
        lines.add(new OrderLine("SKU4", ProductCategory.HOME, 2, 40.0));

        Order order = new Order(orderId, orderDate, lines);

        assertEquals(2, order.getLines().size());
        assertEquals(orderDate, order.getOrderDate());
    }

    @Test
    void testGetCustomerId() {
        String expectedCustomerId = "C1234";
        LocalDate orderDate = LocalDate.of(2023, 9, 1);
        List<OrderLine> lines = singletonList(new OrderLine("SKU1", ProductCategory.GROCERY, 1, 10.0));
        Order order = new Order(expectedCustomerId, orderDate, lines);

        String actualCustomerId = order.getOrderId();

        assertEquals(expectedCustomerId, actualCustomerId);
    }

    @Test
    void testGetOrderDate() {
        LocalDate expectedDate = LocalDate.of(2023, 10, 1);
        List<OrderLine> lines = singletonList(new OrderLine("SKU2", ProductCategory.ELECTRONICS, 1, 15.0));
        Order order = new Order("ORDER_001", expectedDate, lines);

        LocalDate result = order.getOrderDate();

        assertEquals(expectedDate, result);
    }

    @Test
    void testGetLinesReturnsCorrectLines() {
        List<OrderLine> expectedLines = Arrays.asList(
                new OrderLine("SKU1", ProductCategory.ELECTRONICS, 2, 100.0),
                new OrderLine("SKU2", ProductCategory.FASHION, 1, 80.0));
        Order order = new Order("ORDER_002", LocalDate.now(), expectedLines);

        List<OrderLine> actualLines = order.getLines();

        assertEquals(expectedLines, actualLines);
    }

    @Test
    void getLinesReturnsProvidedLines() {
        List<OrderLine> expectedLines = Arrays.asList(
                new OrderLine("SKU3", ProductCategory.SPORTS, 1, 120.0));
        Order order = new Order("ORDER_003", LocalDate.now(), expectedLines);

        List<OrderLine> actualLines = order.getLines();

        assertEquals(expectedLines, actualLines);
    }

    @Test
    void getSubtotalReturnsCorrectValue() {
        List<OrderLine> lines = Arrays.asList(
                new OrderLine("SKU4", ProductCategory.ELECTRONICS, 1, 200.0),
                new OrderLine("SKU5", ProductCategory.FASHION, 2, 75.0));
        Order order = new Order("ORDER_004", LocalDate.now(), lines);

        double subtotal = order.getSubtotal();

        assertTrue(compare(subtotal, 350.0) == 0, () -> "Expected subtotal 350.0 but was " + subtotal);
    }

    @Test
    void testGetSubtotal() {
        List<OrderLine> lines = Arrays.asList(
                new OrderLine("SKU6", ProductCategory.GROCERY, 3, 20.0),
                new OrderLine("SKU7", ProductCategory.GROCERY, 1, 15.0));
        Order order = new Order("ORDER_005", LocalDate.now(), lines);

        double subtotal = order.getSubtotal();

        assertTrue(compare(subtotal, 75.0) == 0, () -> "Expected subtotal 75.0 but was " + subtotal);
    }

    @Test
    void testGetSubtotalForCategory() {
        List<OrderLine> lines = Arrays.asList(
                new OrderLine("SKU8", ProductCategory.ELECTRONICS, 1, 100.0),
                new OrderLine("SKU9", ProductCategory.ELECTRONICS, 2, 50.0),
                new OrderLine("SKU10", ProductCategory.FASHION, 1, 60.0));
        Order order = new Order("ORDER_006", LocalDate.now(), lines);

        double subtotalForCategory = order.getSubtotalForCategory(ProductCategory.ELECTRONICS);

        assertTrue(compare(subtotalForCategory, 200.0) == 0,
                () -> "Expected subtotal for electronics to be 200.0 but was " + subtotalForCategory);
    }

    @Test
    void testGetDominantCategory() {
        List<OrderLine> lines = Arrays.asList(
                new OrderLine("SKU11", ProductCategory.ELECTRONICS, 2, 150.0),
                new OrderLine("SKU12", ProductCategory.FASHION, 1, 80.0));
        Order order = new Order("ORDER_007", LocalDate.now(), lines);

        Optional<ProductCategory> dominantCategory = order.getDominantCategory();

        assertTrue(dominantCategory.isPresent());
        assertEquals(ProductCategory.ELECTRONICS, dominantCategory.orElseThrow());
    }

    @Test
    void testGetDominantCategoryWithSingleCategory() {
        List<OrderLine> lines = singletonList(new OrderLine("SKU13", ProductCategory.FASHION, 1, 120.0));
        Order order = new Order("ORDER_008", LocalDate.now(), lines);

        Optional<ProductCategory> dominantCategory = order.getDominantCategory();

        assertTrue(dominantCategory.isPresent());
        assertEquals(ProductCategory.FASHION, dominantCategory.orElseThrow());
    }
}
