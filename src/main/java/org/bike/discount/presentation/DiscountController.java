package org.bike.discount.presentation;

import java.util.List;
import java.util.Objects;
import org.bike.discount.api.DiscountApi;
import org.bike.discount.api.dto.DiscountData;
import org.bike.discount.api.dto.DiscountRequest;

/** Coordinates requests and view state; discount rules are enforced by the server. */
public final class DiscountController {
    public interface View {
        void setBusy(boolean busy);
        void showDiscounts(List<DiscountData> discounts, Long selectedId);
        void mutationSucceeded();
        void showError(String message);
    }

    private final DiscountApi api;
    private final View view;
    private boolean busy;

    public DiscountController(DiscountApi api, View view) {
        this.api = Objects.requireNonNull(api);
        this.view = Objects.requireNonNull(view);
    }

    public void refresh(Long selectedId) {
        if (busy) return;
        setBusy(true);
        load(selectedId);
    }

    public void create(DiscountRequest request) {
        if (busy) return;
        setBusy(true);
        api.create(request, this::saved, this::failed);
    }

    public void update(Long id, DiscountRequest request) {
        if (busy) return;
        setBusy(true);
        api.update(id, request, this::saved, this::failed);
    }

    public void delete(Long id) {
        if (busy) return;
        setBusy(true);
        api.delete(id, () -> {
            view.mutationSucceeded();
            load(null);
        }, this::failed);
    }

    private void saved(DiscountData discount) {
        view.mutationSucceeded();
        load(discount.id());
    }

    private void load(Long selectedId) {
        api.list(discounts -> {
            view.showDiscounts(discounts, selectedId);
            setBusy(false);
        }, this::failed);
    }

    private void failed(Exception error) {
        setBusy(false);
        view.showError(error.getMessage());
    }

    private void setBusy(boolean value) {
        busy = value;
        view.setBusy(value);
    }
}
