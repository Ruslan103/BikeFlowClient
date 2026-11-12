package org.bike.workorder.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record WorkOrder(String number, LocalDateTime createdAt, WorkOrderDraft draft, String trelloCardId) {
    public WorkOrder(String number, LocalDateTime createdAt, WorkOrderDraft draft) {
        this(number, createdAt, draft, null);
    }
    public WorkOrder {
        Objects.requireNonNull(number);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(draft);
        if (number.isBlank()) throw new IllegalArgumentException("Номер бланка не указан.");
    }
}
