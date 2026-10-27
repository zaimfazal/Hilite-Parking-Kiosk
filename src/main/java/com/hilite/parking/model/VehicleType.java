package com.hilite.parking.model;

/** The two vehicle categories accepted by the kiosk. */
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
