package org.bike.catalog.api.dto;

import java.math.BigDecimal;

public record CreatePriceRequest(BigDecimal price, String validFrom) { }
