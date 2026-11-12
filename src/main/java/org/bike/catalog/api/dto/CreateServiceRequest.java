package org.bike.catalog.api.dto;

import java.math.BigDecimal;

public record CreateServiceRequest(String name, String description, BigDecimal price, String validFrom) { }
