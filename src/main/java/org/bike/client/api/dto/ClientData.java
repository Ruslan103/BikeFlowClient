package org.bike.client.api.dto;

import java.util.List;

public record ClientData(Long id, Long publicId, String lastName, String firstName, String middleName,
                         String comment, List<ClientBicycleData> bicycles, List<ClientContactData> contacts,
                         String type, String organizationName, String representative, Long version) {
    public ClientData(Long id, Long publicId, String lastName, String firstName, String middleName,
            String comment, List<ClientBicycleData> bicycles, List<ClientContactData> contacts) {
        this(id, publicId, lastName, firstName, middleName, comment, bicycles, contacts, "PERSON", null, null, 0L);
    }
    public ClientData {
        bicycles = bicycles == null ? List.of() : List.copyOf(bicycles);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
    }
}
