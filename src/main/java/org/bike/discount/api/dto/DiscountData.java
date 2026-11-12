package org.bike.discount.api.dto;

import java.math.BigDecimal;

public record DiscountData(Long id, String name, BigDecimal percentage) { }
