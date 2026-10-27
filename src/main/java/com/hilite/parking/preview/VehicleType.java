package com.hilite.parking.preview;

/** Phase 1 preview-only vehicle category. Replaced by the real domain model in Phase 2. */
public enum VehicleType {
    CAR("Car"),
    BIKE("Bike / Scooter");

    private final String displayName;

    VehicleType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
