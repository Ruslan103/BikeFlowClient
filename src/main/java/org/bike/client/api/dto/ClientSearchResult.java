package org.bike.client.api.dto;

import java.util.List;

public record ClientSearchResult(List<ClientData> exact, List<ClientData> similar, boolean autoSelect) {
    public ClientSearchResult(List<ClientData> exact,List<ClientData> similar){this(exact,similar,false);}
    public ClientSearchResult { exact = List.copyOf(exact); similar = List.copyOf(similar); }
    public List<ClientData> all() {
        var result = new java.util.ArrayList<>(exact);
        result.addAll(similar);
        return List.copyOf(result);
    }
}
