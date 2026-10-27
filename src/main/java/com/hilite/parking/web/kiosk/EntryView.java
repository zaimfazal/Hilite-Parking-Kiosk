package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.ParkingService;
import com.hilite.parking.web.ui.ActionButton;
import com.hilite.parking.web.ui.Icons;
import com.hilite.parking.web.ui.InlineMessage;
import com.hilite.parking.web.ui.KioskPage;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Vehicle Entry kiosk: simulated plate scan, vehicle type, EV preference, then one allocation
 * request to {@link ParkingService}. On success the browser moves to
 * {@code allocation/<sessionId>}, which only reads the saved session.
 */
@Route("entry")
@PageTitle("Vehicle Entry · HiLITE Mall")
public class EntryView extends KioskPage {

    static final int SCAN_MILLIS = 2200;
    static final String DEMO_SCAN_PLATE = "KL 11 AB 1234";
    static final String FIND_LABEL = "Find My Parking";

    private final ParkingService parkingService;

    final ScanCard scanCard = new ScanCard(SCAN_MILLIS);
    final NativeButton carCard;
    final NativeButton bikeCard;
    final NativeButton evNo = segment("No");
    final NativeButton evYes = segment("Yes");
    final PlateInput plateField = new PlateInput();
    final Span plateHint = text("The number plate appears here after the simulated scan.", "field-hint");
    final InlineMessage error = new InlineMessage(InlineMessage.Kind.ERROR);
    final ActionButton findButton =
            new ActionButton(FIND_LABEL, Icons.arrowRight(19), "btn-primary", "kiosk-cta");

    private VehicleType vehicleType = VehicleType.CAR;
    private boolean charging;
    private boolean scanComplete;
    private boolean busy;
    private long scanStartedNanos;

    public EntryView(ParkingService parkingService, Clock clock, ZoneId mallTimeZone) {
        super(clock, mallTimeZone, "Demonstration interface — simulated number-plate scanning.");
        this.parkingService = parkingService;
        addClassName("entry-view");

        carCard = vehicleCard(Icons.car(40), "Car", "Cars, SUVs & hatchbacks", VehicleType.CAR);
        bikeCard = vehicleCard(Icons.bike(40), "Bike / Scooter", "Bikes & scooters", VehicleType.BIKE);

        body().addClassName("entry-body");
        body().add(hero(), panel());
        refreshSelection();
        findButton.setEnabled(false);
        findButton.addClickListener(event -> handleFindParking());
        plateField.addValueChangeListener(event -> {
            scanCard.showPlate(event.getValue());
            error.hide();
        });
    }

    private Component hero() {
        H1 title = new H1(text("Park smart.", "hero-line"), text("Shop easy.", "hero-line"));
        title.addClassName("hero-title");
        Paragraph sub = new Paragraph(
                "A calmer arrival, a shorter walk, and your bay waiting before you reach the door.");
        sub.addClassName("hero-sub");
        return box("kiosk-hero",
                text("A GREATER TOMORROW TOGETHER", "hero-eyebrow"), title, sub, scanCard);
    }

    private Component panel() {
        H2 title = new H2("Vehicle Entry");
        title.addClassName("panel-title");
        Paragraph subtitle = new Paragraph("Select your vehicle and confirm your number plate.");
        subtitle.addClassName("panel-subtitle");

        Div vehicleRow = box("vehicle-row", carCard, bikeCard);
        vehicleRow.getElement().setAttribute("role", "group");
        vehicleRow.getElement().setAttribute("aria-labelledby", "entry-vehicle-label");
        Span vehicleLabel = text("Select your vehicle type", "field-label");
        vehicleLabel.setId("entry-vehicle-label");

        Span evLabel = text("Needs EV charging?", "field-label");
        evLabel.setId("entry-ev-label");
        Div segments = box("segmented-control", evNo, evYes);
        segments.getElement().setAttribute("role", "group");
        segments.getElement().setAttribute("aria-labelledby", "entry-ev-label");
        evNo.addClickListener(event -> selectCharging(false));
        evYes.addClickListener(event -> selectCharging(true));
        Div evRow = box("ev-row",
                box("ev-copy", Icons.bolt(18), box("ev-text", evLabel,
                        text("Available for cars and bikes, subject to bay availability.", "field-hint"))),
                segments);

        Div spacer = box("panel-spacer");
        return box("glass-panel entry-panel",
                stepRow("01", "VEHICLE ENTRY"), title, subtitle,
                vehicleLabel, vehicleRow, evRow,
                plateField, plateHint, error, spacer, findButton);
    }

    static Div stepRow(String index, String eyebrow) {
        Span rule = new Span();
        rule.addClassName("step-rule");
        Span spacer = new Span();
        spacer.addClassName("step-spacer");
        Div row = box("step-row", text(index, "step-index"), rule, spacer, text(eyebrow, "eyebrow"));
        row.getElement().setAttribute("aria-hidden", "true");
        return row;
    }

    private NativeButton vehicleCard(Component icon, String name, String caption, VehicleType type) {
        Span check = new Span();
        check.addClassName("selection-check");
        check.add(Icons.check(12));
        NativeButton card = new NativeButton();
        card.addClassName("vehicle-card");
        Span iconBox = new Span(icon);
        iconBox.addClassName("vehicle-icon");
        card.add(check, iconBox, text(name, "vehicle-name"), text(caption, "vehicle-caption"));
        card.addClickListener(event -> selectVehicle(type));
        return card;
    }

    private static NativeButton segment(String label) {
        NativeButton button = new NativeButton(label);
        button.addClassName("segment");
        return button;
    }

    private void selectVehicle(VehicleType type) {
        vehicleType = type;
        refreshSelection();
        error.hide();
    }

    private void selectCharging(boolean wanted) {
        charging = wanted;
        refreshSelection();
        error.hide();
    }

    private void refreshSelection() {
        pressed(carCard, vehicleType == VehicleType.CAR);
        pressed(bikeCard, vehicleType == VehicleType.BIKE);
        pressed(evNo, !charging);
        pressed(evYes, charging);
    }

    static void pressed(NativeButton button, boolean on) {
        button.getElement().setAttribute("aria-pressed", Boolean.toString(on));
        if (on) {
            button.addClassName("is-selected");
        } else {
            button.removeClassName("is-selected");
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (!scanComplete) {
            scanStartedNanos = System.nanoTime();
        }
    }

    @Override
    protected void onTick() {
        if (!scanComplete && (System.nanoTime() - scanStartedNanos) / 1_000_000 >= SCAN_MILLIS) {
            completeScan();
        }
    }

    /** Finishes the simulated scan. Package-private so tests need not wait. */
    void completeScan() {
        if (scanComplete) {
            return;
        }
        scanComplete = true;
        scanCard.complete("You can edit the scanned plate if needed.", DEMO_SCAN_PLATE);
        plateField.setEnabled(true);
        plateField.setValue(DEMO_SCAN_PLATE);
        plateField.focus();
        plateHint.setText("Scanned in this demonstration. Edit the plate if required.");
        findButton.setEnabled(true);
    }

    void handleFindParking() {
        if (busy) {
            return;
        }
        if (!scanComplete) {
            error.show("Please wait for the simulated scan to finish.");
            return;
        }
        String rawPlate = plateField.getValue();
        if (rawPlate == null || rawPlate.isBlank()) {
            error.show("Enter your vehicle number plate to continue.");
            plateField.focus();
            return;
        }
        if (vehicleType == null) {
            error.show("Select Car or Bike / Scooter to continue.");
            return;
        }

        busy = true;
        error.hide();
        setInputsEnabled(false);
        findButton.showBusy("Finding your bay…");
        try {
            AllocationOutcome outcome = parkingService.allocateParking(rawPlate, vehicleType, charging);
            if (outcome instanceof AllocationOutcome.Allocated allocated) {
                String sessionId = allocated.session().sessionId();
                getUI().ifPresentOrElse(
                        ui -> ui.navigate(AllocationView.class,
                                new RouteParameters(AllocationView.SESSION_PARAM, sessionId)),
                        this::restoreControls);
                return;
            }
            restoreControls();
            if (outcome instanceof AllocationOutcome.Rejected rejected) {
                error.show(rejected.message());
            } else {
                error.show("Parking is not ready yet. Please try again.");
            }
        } catch (DataAccessException failure) {
            restoreControls();
            error.show("Could not save your parking session. Please try again.");
        }
    }

    private void restoreControls() {
        busy = false;
        setInputsEnabled(true);
        findButton.restore(FIND_LABEL, Icons.arrowRight(19));
    }

    private void setInputsEnabled(boolean enabled) {
        carCard.setEnabled(enabled);
        bikeCard.setEnabled(enabled);
        evNo.setEnabled(enabled);
        evYes.setEnabled(enabled);
        plateField.setEnabled(enabled);
    }

    boolean isScanComplete() {
        return scanComplete;
    }
}
