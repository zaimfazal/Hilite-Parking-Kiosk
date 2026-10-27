package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.AdminAccessService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The public kiosk address ({@code /}). Forwards to Vehicle Entry or Vehicle Exit according to
 * the kiosk mode persisted by the administrator. Only {@link AdminAccessService#currentMode()}
 * is used here: the mode is public information.
 */
@Route("")
@PageTitle("HiLITE Mall Smart Parking")
public class KioskHomeView extends Div implements BeforeEnterObserver {

    private final AdminAccessService adminAccess;

    public KioskHomeView(AdminAccessService adminAccess) {
        this.adminAccess = adminAccess;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        KioskMode mode;
        try {
            mode = adminAccess.currentMode();
        } catch (DataAccessException failure) {
            // The entry kiosk is the safe default when the setting cannot be read.
            mode = KioskMode.ENTRY;
        }
        event.forwardTo(mode == KioskMode.EXIT ? ExitView.class : EntryView.class);
    }
}
