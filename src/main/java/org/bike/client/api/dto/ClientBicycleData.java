package org.bike.client.api.dto;

public record ClientBicycleData(Long id, Long brandId, String brandName, String comment, Long version) {
    public ClientBicycleData(Long id, Long brandId, String brandName, String comment) { this(id, brandId, brandName, comment, 0L); }
}
