package org.bike.workorder.model;

public record WorkOrderDetails(String firstName, String lastName, String middleName,
        String bicycleBrand, String bicycleComment, Long phoneContactId, String phone,
        Long otherContactId, String otherTypeCode, String otherValue) { }
