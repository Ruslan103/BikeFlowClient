package org.bike.catalog.api.dto;

import java.util.List;

public record WorkshopServiceBatchData(
        List<WorkshopServiceData> services,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
    public WorkshopServiceBatchData {
        services = services == null ? List.of() : List.copyOf(services);
    }
}
