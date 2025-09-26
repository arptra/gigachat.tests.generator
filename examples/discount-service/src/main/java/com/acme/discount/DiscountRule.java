package com.acme.discount;

import java.time.LocalDate;

public interface DiscountRule {
    DiscountOutcome apply(Order order, CustomerProfile customer, LocalDate calculationDate);

    String name();
}
