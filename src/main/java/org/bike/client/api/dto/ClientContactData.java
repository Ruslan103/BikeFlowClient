package org.bike.client.api.dto;

public record ClientContactData(Long id, Long typeId, String typeCode, String typeName, String value, String label) { }
