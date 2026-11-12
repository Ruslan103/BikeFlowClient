package org.bike.client.api.dto;

import java.util.List;

public record ClientCardRequest(String type, Long version, String firstName, String lastName,
        String middleName, String organizationName, String representative, String comment,
        List<ContactInput> contacts, boolean allowDuplicateContacts) {
    public record ContactInput(Long id, Long typeId, String value, String label) { }
}
