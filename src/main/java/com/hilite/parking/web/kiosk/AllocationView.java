package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.ParkingService;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.web.ui.ActionButton;
import com.hilite.parking.web.ui.Icons;
import com.hilite.parking.web.ui.Plates;
import com.hilite.parking.web.ui.KioskPage;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * Bay allocation confirmation. Reads the saved session for the id in the URL through
 * {@link ParkingService#findAllocation(String)} and never allocates: a refresh shows the same
 * bay, an unknown id shows a friendly "not found" state, and a closed session says so.
 */
@Route("allocation/:sessionId")
@PageTitle("Bay Allocated · HiLITE Mall")
public class AllocationView extends KioskPage implements BeforeEnterObserver {

    static final String SESSION_PARAM = "sessionId";

    private final ParkingService parkingService;
    private final ZoneId zone;

    final Span bayCode = text("", "bay-code");
    final Span floorName = text("", "floor-name");
    final Span directions = text("", "direction-copy");
    final ActionButton printButton =
            new ActionButton("Print Parking Ticket", Icons.print(19), "btn-secondary", "kiosk-cta");
    final ActionButton doneButton =
            new ActionButton("Done", Icons.arrowRight(19), "btn-primary", "kiosk-cta");
    final Dialog ticketDialog = new Dialog();
    final Span ticketNote = text("Demo ticket · generated from the saved parking session.", "ticket-note");
    final ActionButton simulatePrintButton =
            new ActionButton("Simulate Print", Icons.print(17), "btn-primary");
    final ActionButton closeTicketButton =
            new ActionButton("Close", Icons.close(16), "btn-secondary");
    /** Title of the not-found / closed / error state, or empty while showing an allocation. */
    final H2 stateTitle = new H2();

    public AllocationView(ParkingService parkingService, Clock clock, ZoneId mallTimeZone) {
        super(clock, mallTimeZone, "Demonstration interface — your parking session is saved locally.");
        this.parkingService = parkingService;
        this.zone = mallTimeZone;
        addClassName("allocation-view");
        body().addClassName("allocation-body");
        doneButton.addClickListener(event -> goToEntry());
        printButton.addClickListener(event -> openTicket());
        configureTicketDialog();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String sessionId = event.getRouteParameters().get(SESSION_PARAM).orElse("");
        body().removeAll();
        stateTitle.setText("");
        bayCode.setText("");
        Optional<AllocationOutcome.Allocated> found;
        try {
            found = parkingService.findAllocation(sessionId);
        } catch (DataAccessException failure) {
            showProblem("We could not load this parking session",
                    "Something went wrong while reading the saved session. Please try again in a moment.");
            return;
        }
        if (found.isEmpty()) {
            showProblem("Parking session not found",
                    "We could not find this parking session. Please start again at the entry kiosk.");
            return;
        }
        AllocationOutcome.Allocated allocation = found.get();
        if (allocation.session().exitTime() != null) {
            showProblem("This parking session has already been closed",
                    "Bay " + allocation.slot().slotId() + " is no longer reserved for this vehicle.");
            return;
        }
        showAllocation(allocation);
    }

    private void showProblem(String title, String message) {
        stateTitle.setText(title);
        stateTitle.addClassName("panel-title");
        Paragraph copy = new Paragraph(message);
        copy.addClassName("panel-subtitle");
        Div badge = box("problem-badge", Icons.info(26));
        ActionButton home = new ActionButton("Back to vehicle entry", Icons.arrowLeft(17),
                "btn-primary", "kiosk-cta");
        home.addClickListener(event -> goToEntry());
        body().add(box("glass-panel problem-panel", badge, stateTitle, copy, home));
    }

    private void showAllocation(AllocationOutcome.Allocated allocation) {
        ParkingSession session = allocation.session();
        ParkingSlot slot = allocation.slot();
        String zoneName = slot.zone().name();
        String charging = session.chargingRequested() ? "Requested" : "Not requested";
        String entry = FormatUtil.dateTime(session.entryTime(), zone);

        // --- success heading -------------------------------------------------------------
        Div badge = box("success-badge", Icons.check(30));
        H1 title = new H1("Parking Slot Allocated");
        title.addClassName("allocation-title");
        Div successRow = box("success-row", badge,
                box("success-copy", title,
                        text("Your parking space is ready. Drive safely.", "allocation-subtitle")));

        // --- floor / bay highlight -------------------------------------------------------
        floorName.setText(slot.floor().displayName());
        bayCode.setText(slot.slotId());
        Div highlight = box("bay-highlight",
                box("bay-highlight-col", text("FLOOR", "detail-label"), floorName,
                        text(slot.floor().code(), "floor-code")),
                box("vertical-rule"),
                box("bay-highlight-col", text("BAY", "detail-label"), bayCode));

        directions.setText(directionsFor(slot));
        Div directionRow = box("direction-row", box("direction-icon", Icons.arrowUp(22)), directions);

        Div details = box("allocation-details",
                detail(session.vehicleType() == VehicleType.BIKE ? Icons.bike(19) : Icons.car(19),
                        "Vehicle number", Plates.display(session.registration()), null),
                detail(Icons.steering(19), "Vehicle type", session.vehicleType().displayName(), null),
                detail(Icons.clock(18), "Entry time", entry, null),
                detail(Icons.bolt(18), "EV charging", charging,
                        session.chargingRequested() ? "is-positive" : "is-muted"));

        Div actions = box("allocation-actions", printButton, doneButton);
        Div panel = box("glass-panel allocation-panel", successRow, highlight, directionRow, details, actions);

        // --- overhead signage --------------------------------------------------------------
        Div rule = box("signage-rule");
        Div board = box("signage-board",
                text(slot.floor().displayName().toUpperCase(Locale.ROOT) + " · " + slot.floor().code(),
                        "signage-eyebrow"),
                box("signage-main",
                        box("signage-arrow", Icons.arrowUp(38)), rule,
                        text(slot.floor().code(), "signage-floor"),
                        text(zoneName, "zone-badge"),
                        text("ZONE", "signage-zone-word")),
                text(slot.slotId(), "signage-bay"),
                text("FOLLOW THE " + zoneName + "-ZONE AISLE", "signage-caption"));
        board.getElement().setAttribute("aria-hidden", "true");
        Div signage = box("signage-column", board,
                text("Your bay is reserved for this demonstration session.", "signage-note"));

        body().add(panel, signage);
        buildTicket(session, slot, entry, charging);
    }

    private static Div detail(Component icon, String label, String value, String valueClass) {
        Span valueSpan = text(value, "detail-value");
        if (valueClass != null) {
            valueSpan.addClassName(valueClass);
        }
        return box("detail-item", box("detail-icon", icon),
                box("detail-text", text(label, "detail-label"), valueSpan));
    }

    static String directionsFor(ParkingSlot slot) {
        String zoneName = slot.zone().name();
        return slot.floor() == Floor.GROUND
                ? "Remain on the Ground Floor and follow the Zone " + zoneName + " signs to your bay."
                : "Proceed to the First Floor, follow the Zone " + zoneName
                        + " signs, and locate your bay.";
    }

    private void buildTicket(ParkingSession session, ParkingSlot slot, String entry, String charging) {
        ticketDialog.removeAll();

        Div header = box("ticket-header",
                box("ticket-brand-box", text("HiLITE MALL", "ticket-brand"),
                        text("SMART PARKING", "ticket-brand-sub")),
                text("PARKING TICKET", "ticket-type"));
        Div bay = box("ticket-bay-block", text("ASSIGNED BAY", "ticket-label"),
                text(slot.slotId(), "ticket-bay"),
                text(slot.floor().displayName() + " · Zone " + slot.zone().name(), "ticket-floor"));
        Div grid = box("ticket-grid",
                ticketItem("VEHICLE", Plates.display(session.registration())),
                ticketItem("TYPE", session.vehicleType().displayName()),
                ticketItem("ENTRY TIME", entry),
                ticketItem("EV CHARGING", charging));
        Div card = box("ticket-card", header, box("ticket-rule"), bay, box("ticket-rule dashed"),
                grid, box("ticket-rule dashed"), ticketNote,
                box("ticket-actions", closeTicketButton, simulatePrintButton));
        ticketDialog.add(card);
        ticketNote.setText("Demo ticket · generated from the saved parking session.");
        simulatePrintButton.setLabel("Simulate Print");
        simulatePrintButton.setEnabled(true);
    }

    private void configureTicketDialog() {
        ticketDialog.addClassName("ticket-dialog");
        ticketDialog.setCloseOnEsc(true);
        ticketDialog.setCloseOnOutsideClick(true);
        ticketDialog.setModal(true);
        ticketDialog.getElement().setAttribute("aria-label", "Parking ticket preview");
        closeTicketButton.addClickListener(event -> ticketDialog.close());
        simulatePrintButton.addClickListener(event -> {
            ticketNote.setText("Simulated print complete — this ticket matches the saved parking"
                    + " session. No physical printer was used.");
            simulatePrintButton.setLabel("Printed (demo)");
            simulatePrintButton.setEnabled(false);
        });
        ticketDialog.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                closeTicketButton.focus();
            } else {
                printButton.focus();
            }
        });
        // The dialog belongs to this view, so it is removed with it.
        add(ticketDialog);
    }

    private static Div ticketItem(String label, String value) {
        return box("ticket-item", text(label, "ticket-label"), text(value, "ticket-value"));
    }

    private void openTicket() {
        ticketDialog.open();
    }

    private void goToEntry() {
        getUI().ifPresent(ui -> ui.navigate(EntryView.class));
    }
}
