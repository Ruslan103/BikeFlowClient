package org.bike.workorder.model;

public record ClientSnapshotData(String clientType, String displayName, String firstName, String lastName,
        String middleName, String organizationName, String representative, String contactTypeCode,
        String contactValue, String bicycleBrand, String bicycleComment, String clientComment) { }
