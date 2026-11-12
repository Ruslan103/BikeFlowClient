package org.bike.workorder.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bike.workorder.api.ServiceQuoteApi;
import org.bike.workorder.model.ServiceQuote;
import org.bike.workorder.model.WorkOrderDraft.ServiceLine;

/** Keeps pending calculations separate from the last successfully calculated rows. */
public final class WorkOrderServicesController {
    public interface View {
        void setQuoteBusy(boolean busy);
        void showLines(List<ServiceLine> lines);
        void showQuote(ServiceQuote quote);
        void showQuoteError(Exception error);
    }
    private final ServiceQuoteApi api;
    private final View view;
    private List<ServiceLine> lines = List.of();
    private boolean busy;
    private long revision;

    public WorkOrderServicesController(ServiceQuoteApi api, View view) {
        this.api = Objects.requireNonNull(api);
        this.view = Objects.requireNonNull(view);
    }

    public void display(List<ServiceLine> values) {
        revision++;
        lines = List.copyOf(values);
        view.showLines(lines);
        if (lines.isEmpty()) {
            busy = false;
            view.setQuoteBusy(false);
            view.showQuote(new ServiceQuote(List.of(), 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
            return;
        }
        calculate(lines, false);
    }

    public void add(Long serviceId) {
        if (busy) return;
        var pending = new ArrayList<>(lines);
        pending.add(new ServiceLine(serviceId, null, null, null, null, null, null));
        calculate(pending, true);
    }

    public void changeDiscount(int row, Long discountId) {
        if (busy) return;
        ServiceLine line = lines.get(row);
        if (Objects.equals(line.discountId(), discountId)) return;
        var pending = new ArrayList<>(lines);
        pending.set(row, new ServiceLine(line.serviceId(), discountId, null, line.serviceNameSnapshot(),
                line.priceWithoutDiscount(), null, null));
        calculate(pending, true);
    }

    public void remove(int row) {
        if (busy || row < 0 || row >= lines.size()) return;
        var pending = new ArrayList<>(lines);
        pending.remove(row);
        calculate(pending, true);
    }

    private void calculate(List<ServiceLine> pending, boolean replaceLines) {
        long token = ++revision;
        busy = true;
        view.setQuoteBusy(true);
        api.calculate(List.copyOf(pending), quote -> {
            if (token != revision) return;
            if (replaceLines) { lines = List.copyOf(quote.services()); view.showLines(lines); }
            view.showQuote(quote);
            busy = false;
            view.setQuoteBusy(false);
        }, error -> {
            if (token != revision) return;
            busy = false;
            view.setQuoteBusy(false);
            view.showQuoteError(error);
        });
    }
}
