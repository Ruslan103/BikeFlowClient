package org.bike.client.api.dto;

import org.bike.workorder.model.ClientSnapshotData;

public record ClientSelectionResponse(ClientSnapshotData snapshot, Long expectedClientVersion, Long expectedBicycleVersion) { }
