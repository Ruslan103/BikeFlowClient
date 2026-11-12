package org.bike.workorder.presentation;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.bike.workorder.model.WorkOrder;
import org.bike.workorder.model.WorkOrderDraft;
import org.bike.workorder.service.WorkOrderService;

public final class CreateWorkOrderController implements WorkOrderController {
    public String title() { return "Новый бланк"; }
    public boolean allowsNumberGeneration() { return true; }
    public LocalDateTime creationDate() { return LocalDateTime.now(); }
    public void save(WorkOrderService service, WorkOrderDraft draft,
                     Consumer<WorkOrder> success, Consumer<Exception> failure) {
        service.create(draft, success, failure);
    }
}
