package com.acme.discount.complex;

import com.acme.discount.CustomerProfile;
import com.acme.discount.DiscountEngine;
import com.acme.discount.DiscountResult;
import com.acme.discount.LoyaltyTier;
import com.acme.discount.Order;
import com.acme.discount.ProductCategory;
import com.acme.discount.SeasonalPromotion;
import com.acme.discount.complex.RecommendationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CampaignDiscountCalculatorTest {

    @Mock
    private DiscountEngine discountEngine;

    private CampaignDiscountCalculator calculator;

    @BeforeEach
    void setup() {
        this.calculator = new CampaignDiscountCalculator(discountEngine);
    }

    @Test
    void shouldCalculateDiscountBasedOnSeasonalPromotionAndCustomerProfile() {
        // Arrange
        Set<ProductCategory> categories = new HashSet<>();
        categories.add(ProductCategory.ELECTRONICS);
        SeasonalPromotion seasonalPromotion =
            new SeasonalPromotion("Summer Sale", Month.JUNE, Month.AUGUST, categories, 0.15);

        CustomerProfile customerProfile = new CustomerProfile(
            "customer-123",
            LoyaltyTier.GOLD,
            1000,
            LocalDate.of(2020, Month.JANUARY, 1),
            Collections.emptyMap());

        Order order = new Order("order-456", LocalDate.now(), Collections.emptyList());
        RecommendationPipeline.Plan plan = new RecommendationPipeline.Plan(List.of("ELECTRONICS"), 1.25);

        when(discountEngine.calculate(any(Order.class), any(CustomerProfile.class), any(LocalDate.class)))
            .thenReturn(new DiscountResult(1000.0, 850.0, Collections.emptyList()));

        LocalDate calculationDate = LocalDate.of(2023, Month.JULY, 15);

        // Act
        CampaignDiscountCalculator.Result result =
            calculator.calculate(order, customerProfile, seasonalPromotion, plan, calculationDate);

        // Assert
        DiscountResult discountResult = result.result();
        assertEquals(1000.0, discountResult.getSubtotal());
        assertEquals(850.0, discountResult.getFinalTotal());
        assertEquals(150.0, discountResult.getTotalDiscount());
    }
}
