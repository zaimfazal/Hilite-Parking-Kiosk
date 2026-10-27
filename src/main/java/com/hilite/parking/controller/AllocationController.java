package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.util.GarageBackdrop;
import com.hilite.parking.util.Icons;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/** Allocation confirmation. Every displayed value comes from the saved allocation. */
public class AllocationController implements Initializable, NavigatorAware {

    @FXML private KioskHeaderController headerController;
    @FXML private StackPane backdropHost;
    @FXML private StackPane successBadge;
    @FXML private StackPane directionIcon;
    @FXML private StackPane vehicleIconHost;
    @FXML private StackPane typeIconHost;
    @FXML private StackPane timeIconHost;
    @FXML private StackPane chargingIconHost;
    @FXML private StackPane signArrowHost;
    @FXML private Label floorNameLabel;
    @FXML private Label floorCodeLabel;
    @FXML private Label bayCodeLabel;
    @FXML private Label directionsLabel;
    @FXML private Label registrationLabel;
    @FXML private Label vehicleTypeLabel;
    @FXML private Label entryTimeLabel;
    @FXML private Label chargingLabel;
    @FXML private Label signLevelLabel;
    @FXML private Label signFloorLabel;
    @FXML private Label signZoneLabel;
    @FXML private Label signBayLabel;
    @FXML private Label signDirectionLabel;
    @FXML private Button printTicketButton;
    @FXML private Button doneButton;
    @FXML private StackPane ticketOverlay;
    @FXML private Label ticketBayLabel;
    @FXML private Label ticketFloorLabel;
    @FXML private Label ticketPlateLabel;
    @FXML private Label ticketVehicleLabel;
    @FXML private Label ticketEntryLabel;
    @FXML private Label ticketChargingLabel;
    @FXML private Label printStatusLabel;
    @FXML private Button closeTicketButton;
    @FXML private Button simulatePrintButton;

    private Navigator navigator;
    private AllocationOutcome.Allocated allocation;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        backdropHost.getChildren().add(new GarageBackdrop());
        successBadge.getChildren().add(Icons.check(30, "icon-white"));
        directionIcon.getChildren().add(Icons.arrowUp(22, "icon-white"));
        timeIconHost.getChildren().add(Icons.clock(18, "icon-muted"));
        chargingIconHost.getChildren().add(Icons.bolt(18, "icon-gold"));
        signArrowHost.getChildren().add(Icons.arrowUp(38, "icon-white"));

        printTicketButton.setText("Print Parking Ticket");
        printTicketButton.setGraphic(Icons.print(19, "icon-light"));
        doneButton.setText("Done");
        doneButton.setGraphic(Icons.arrowRight(19, "icon-white"));
        closeTicketButton.setGraphic(Icons.close(16, "icon-ink"));

        ticketOverlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeTicket();
                event.consume();
            }
        });
    }

    @Override
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        if (headerController != null) {
            headerController.setNavigator(navigator);
            headerController.configure(null);
        }
    }

    /**
     * Shows the bay that the entry screen has already saved.
     * Opening this screen is read-only and never allocates another bay.
     */
    public void setAllocation(AllocationOutcome.Allocated allocation) {
        this.allocation = allocation;
        if (this.allocation == null) {
            return;
        }
        ParkingSession session = this.allocation.session();
        ParkingSlot slot = this.allocation.slot();

        floorNameLabel.setText(slot.floor().displayName());
        floorCodeLabel.setText(slot.floor().code());
        bayCodeLabel.setText(slot.slotId());
        directionsLabel.setText(directionsFor(slot));
        registrationLabel.setText(session.registration());
        vehicleTypeLabel.setText(session.vehicleType().displayName());
        entryTimeLabel.setText(FormatUtil.dateTime(session.entryTime()));
        chargingLabel.setText(session.chargingRequested() ? "Requested" : "Not requested");
        chargingLabel.getStyleClass().removeAll("is-positive", "is-muted");
        chargingLabel.getStyleClass().add(session.chargingRequested() ? "is-positive" : "is-muted");

        vehicleIconHost.getChildren().setAll(session.vehicleType() == VehicleType.BIKE
                ? Icons.bike(19, "icon-muted") : Icons.car(19, "icon-muted"));
        typeIconHost.getChildren().setAll(Icons.steering(19, "icon-muted"));

        signFloorLabel.setText(slot.floor().code());
        signZoneLabel.setText(slot.zone().name());
        signBayLabel.setText(slot.slotId());
        signLevelLabel.setText("LEVEL 0" + (slot.floor() == Floor.FIRST ? "2" : "1")
                + " · " + slot.floor().displayName().toUpperCase());
        signDirectionLabel.setText("FOLLOW THE " + slot.zone().name() + "-ZONE AISLE");

        ticketBayLabel.setText(slot.slotId());
        ticketFloorLabel.setText(slot.floor().displayName() + " · Zone " + slot.zone().name());
        ticketPlateLabel.setText(session.registration());
        ticketVehicleLabel.setText(session.vehicleType().displayName());
        ticketEntryLabel.setText(FormatUtil.dateTime(session.entryTime()));
        ticketChargingLabel.setText(session.chargingRequested() ? "Requested" : "Not requested");
    }

    private static String directionsFor(ParkingSlot slot) {
        String zone = slot.zone().name();
        return switch (slot.floor()) {
            case GROUND -> "Remain on the Ground Floor and follow the Zone " + zone
                    + " signs to your bay.";
            case FIRST -> "Proceed to the First Floor, follow the Zone " + zone
                    + " signs, and locate your bay.";
        };
    }

    @FXML
    private void showTicket() {
        ticketOverlay.setVisible(true);
        ticketOverlay.setManaged(true);
        ticketOverlay.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(160), ticketOverlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        closeTicketButton.requestFocus();
    }

    @FXML
    private void closeTicket() {
        ticketOverlay.setVisible(false);
        ticketOverlay.setManaged(false);
        printTicketButton.requestFocus();
    }

    @FXML
    private void simulatePrint() {
        printStatusLabel.setText("Simulated print complete — this ticket matches the saved parking"
                + " session. No physical printer was used.");
        simulatePrintButton.setText("Printed (demo)");
        simulatePrintButton.setDisable(true);
    }

    @FXML
    private void handleDone() {
        if (navigator != null) {
            navigator.showEntry();
        }
    }
}
