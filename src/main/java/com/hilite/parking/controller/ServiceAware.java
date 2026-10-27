package com.hilite.parking.controller;

import com.hilite.parking.service.ParkingService;

/**
 * Implemented by screens that need the parking service.
 *
 * <p>{@link com.hilite.parking.Navigator} supplies the service right after it loads an FXML
 * view, in the same way it supplies itself to {@link NavigatorAware} screens. This avoids a
 * service locator, static singleton or dependency-injection framework for a three-screen
 * demo.</p>
 */
public interface ServiceAware {

    /** Supplies the shared parking service. Called once per screen load, before any user action. */
    void setParkingService(ParkingService parkingService);
}
