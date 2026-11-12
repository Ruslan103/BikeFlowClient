package org.bike.catalog.api.dto;

import java.math.BigDecimal;

public record WorkshopServiceData(Long id, String name, String description,
                                  BigDecimal price, String validFrom, String validTo) { }
