package org.bike.workorder.model;

import java.math.BigDecimal;
import java.util.List;
import org.bike.workorder.model.WorkOrderDraft.ServiceLine;

public record ServiceQuote(List<ServiceLine> services, int serviceCount,
                           BigDecimal totalPrice, BigDecimal totalWithDiscount) { }
