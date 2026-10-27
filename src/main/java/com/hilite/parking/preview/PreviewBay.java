package com.hilite.parking.preview;

import java.time.LocalDateTime;

/** PHASE 1 PREVIEW ONLY. One sample bay on the dashboard floor maps. */
public record PreviewBay(
        String id,
        String floorName,
        String floorCode,
        String zone,
        boolean charging,
        boolean occupied,
        String registration,
        VehicleType vehicleType,
        LocalDateTime entryTime
) {
    public String statusText() {
        return occupied ? "Occupied" : "Available";
    }
}
