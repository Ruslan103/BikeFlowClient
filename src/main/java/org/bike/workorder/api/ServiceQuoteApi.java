package org.bike.workorder.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.workorder.model.WorkOrderDraft.ServiceLine;
import org.bike.workorder.model.ServiceQuote;

public interface ServiceQuoteApi {
    void calculate(List<ServiceLine> lines, Consumer<ServiceQuote> success, Consumer<Exception> failure);
}
