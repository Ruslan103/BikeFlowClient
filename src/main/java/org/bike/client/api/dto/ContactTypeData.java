package org.bike.client.api.dto;

public record ContactTypeData(Long id, String code, String name) {
    @Override public String toString() { return name; }
}
