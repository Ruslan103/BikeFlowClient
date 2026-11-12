package org.bike.catalog.api.dto;

import java.math.BigDecimal;

public record UpdatePriceRequest(BigDecimal price) { }
