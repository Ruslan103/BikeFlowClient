package org.bike.discount.api.dto;

import java.math.BigDecimal;

public record DiscountRequest(String name, BigDecimal percentage) { }
