package com.hilite.parking.controller;

import com.hilite.parking.service.ExitService;

/**
 * Implemented by screens that need the exit service.
 *
 * <p>{@link com.hilite.parking.Navigator} supplies the service right after it loads an FXML
 * view, exactly as it supplies {@link ServiceAware} screens with the parking service. Keeping
 * the two hooks separate means a screen only receives the collaborator it actually uses.</p>
 */
public interface ExitServiceAware {

    /** Supplies the shared exit service. Called once per screen load, before any user action. */
    void setExitService(ExitService exitService);
}
