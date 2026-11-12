package org.bike.client.presentation;

import java.util.List;
import java.util.Objects;
import org.bike.client.api.ClientSearchApi;
import org.bike.client.api.dto.ClientData;

public final class ClientSearchController {
    public interface View {
        void setSearchBusy(boolean busy);
        void applyClient(ClientData client);
        ClientData chooseClient(List<ClientData> clients);
        boolean askToShowComment();
        void showComment(String comment);
        void showNotFound();
        void showSearchError(Exception error);
    }
    private final ClientSearchApi api;
    private final View view;
    private boolean busy;
    private long revision;

    public ClientSearchController(ClientSearchApi api, View view) {
        this.api = Objects.requireNonNull(api);
        this.view = Objects.requireNonNull(view);
    }

    public void reset() { revision++; busy = false; }

    public void search(String phone) {
        if (busy) return;
        long token = ++revision;
        busy = true;
        view.setSearchBusy(true);
        api.search(phone, clients -> {
            if (revision != token) return;
            busy = false;
            view.setSearchBusy(false);
            if (clients.isEmpty()) { view.showNotFound(); return; }
            if (clients.size() > 1) {
                ClientData selected = view.chooseClient(clients);
                if (selected != null) view.applyClient(selected);
                return;
            }
            ClientData client = clients.getFirst();
            view.applyClient(client);
            if (client.comment() != null && !client.comment().isBlank() && view.askToShowComment()) {
                view.showComment(client.comment());
            }
        }, error -> {
            if (revision != token) return;
            busy = false;
            view.setSearchBusy(false);
            view.showSearchError(error);
        });
    }
}
