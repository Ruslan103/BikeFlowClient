package org.bike.workorder.presentation;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.bike.workorder.model.WorkOrder;
import org.bike.workorder.model.WorkOrderDraft;
import org.bike.workorder.service.WorkOrderService;

public sealed interface WorkOrderController permits CreateWorkOrderController, EditWorkOrderController {
    String title();
    boolean allowsNumberGeneration();
    LocalDateTime creationDate();
    void save(WorkOrderService service, WorkOrderDraft draft,
              Consumer<WorkOrder> success, Consumer<Exception> failure);
}
