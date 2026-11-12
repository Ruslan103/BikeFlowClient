package org.bike.workorder.presentation;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import org.bike.workorder.model.WorkOrder;
import org.bike.workorder.model.WorkOrderDraft;
import org.bike.workorder.service.WorkOrderService;

public final class EditWorkOrderController implements WorkOrderController {
    private final WorkOrder order;
    public EditWorkOrderController(WorkOrder order) { this.order = Objects.requireNonNull(order); }
    public String title() { return "Редактирование · №" + order.number(); }
    public boolean allowsNumberGeneration() { return false; }
    public LocalDateTime creationDate() { return order.createdAt(); }
    public void save(WorkOrderService service, WorkOrderDraft draft,
                     Consumer<WorkOrder> success, Consumer<Exception> failure) {
        service.update(order.number(), draft, success, failure);
    }
}
