package org.bike.catalog.api.dto;

import java.math.BigDecimal;

public record ServicePriceData(Long id, Long serviceId, BigDecimal price, String validFrom, String validTo) { }
