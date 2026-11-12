package org.bike.discount.api.dto;

import java.util.List;

public record DiscountBatchData(
        List<DiscountData> discounts,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
    public DiscountBatchData {
        discounts = discounts == null ? List.of() : List.copyOf(discounts);
    }
}
