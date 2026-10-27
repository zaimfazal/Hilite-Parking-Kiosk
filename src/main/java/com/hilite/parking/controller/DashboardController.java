package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.preview.PreviewBay;
import com.hilite.parking.preview.PreviewData;
import com.hilite.parking.preview.PreviewLongStay;
import com.hilite.parking.preview.PreviewStats;
import com.hilite.parking.preview.VehicleType;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.util.Icons;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Admin dashboard. All figures and bays are read from PreviewData; nothing here is a live
 * database statistic or a real reset action.
 */
public class DashboardController implements Initializable, NavigatorAware {

    @FXML private StackPane dashboardNavIcon;
    @FXML private Button dashboardNavButton;
    @FXML private Button lockButton;
    @FXML private ToggleGroup modeGroup;
    @FXML private ToggleButton entryModeButton;
    @FXML private ToggleButton exitModeButton;
    @FXML private Button resetButton;
    @FXML private ScrollPane workspaceScroll;
    @FXML private Label previewNotice;

    @FXML private StackPane occupancyIcon;
    @FXML private StackPane availableIcon;
    @FXML private StackPane entriesIcon;
    @FXML private StackPane exitsIcon;
    @FXML private Label occupancyValue;
    @FXML private Label occupancyHint;
    @FXML private Label availableValue;
    @FXML private Label availableHint;
    @FXML private Label entriesValue;
    @FXML private Label entriesHint;
    @FXML private Label exitsValue;
    @FXML private Label exitsHint;

    @FXML private GridPane groundGrid;
    @FXML private GridPane firstGrid;

    @FXML private Label bayDetailTitle;
    @FXML private Label bayDetailStatus;
    @FXML private Label bayDetailMeta;
    @FXML private Label bayDetailSession;

    @FXML private StackPane snapshotCarIcon;
    @FXML private StackPane snapshotBikeIcon;
    @FXML private StackPane evIcon;
    @FXML private Label carsSummaryLabel;
    @FXML private Label bikesSummaryLabel;
    @FXML private Label evUsageLabel;
    @FXML private ProgressBar evUsageBar;
    @FXML private StackPane peakIcon;
    @FXML private Label peakHourValue;
    @FXML private Label peakHint;
    @FXML private Label longStayCount;
    @FXML private VBox alertsList;

    @FXML private StackPane resetOverlay;
    @FXML private StackPane dialogIcon;
    @FXML private Button cancelResetButton;
    @FXML private Button confirmResetButton;

    private Navigator navigator;
    private final ToggleGroup bayGroup = new ToggleGroup();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureChrome();
        populateSummary(PreviewData.stats());
        buildFloorGrid(groundGrid, "G");
        buildFloorGrid(firstGrid, "F1");
        populateAlerts();
        showBayDetails(null);

        bayGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle != null && newToggle.getUserData() instanceof PreviewBay bay) {
                showBayDetails(bay);
            }
        });
    }

    @Override
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        entryModeButton.setSelected(navigator.getKioskMode() == Navigator.KioskMode.ENTRY);
        exitModeButton.setSelected(navigator.getKioskMode() == Navigator.KioskMode.EXIT);
    }

    private void configureChrome() {
        dashboardNavButton.setText("Dashboard");
        dashboardNavButton.setAlignment(Pos.CENTER_LEFT);
        dashboardNavIcon.getChildren().add(Icons.dashboard(19, "icon-white"));
        lockButton.setGraphic(Icons.logout(16, "icon-light"));
        resetButton.setGraphic(Icons.reset(15, "icon-danger"));
        entryModeButton.setGraphic(Icons.steering(16, "icon-ink"));
        exitModeButton.setGraphic(Icons.logout(16, "icon-ink"));
        occupancyIcon.getChildren().add(Icons.car(21, "icon-ink"));
        availableIcon.getChildren().add(Icons.check(20, "icon-success"));
        entriesIcon.getChildren().add(Icons.arrowUp(20, "icon-blue"));
        exitsIcon.getChildren().add(Icons.arrowRight(20, "icon-muted"));
        snapshotCarIcon.getChildren().add(Icons.car(15, "icon-muted"));
        snapshotBikeIcon.getChildren().add(Icons.bike(15, "icon-muted"));
        evIcon.getChildren().add(Icons.bolt(15, "icon-blue"));
        peakIcon.getChildren().add(Icons.clock(18, "icon-blue"));
        dialogIcon.getChildren().add(Icons.alert(24, "icon-danger"));
        cancelResetButton.setGraphic(Icons.close(15, "icon-ink"));
    }

    private void populateSummary(PreviewStats stats) {
        int total = stats.totalBays();
        occupancyValue.setText(Integer.toString(stats.occupancy()));
        occupancyHint.setText("of " + total + " bays · " + percent(stats.occupancy(), total) + "%");
        availableValue.setText(Integer.toString(stats.available()));
        availableHint.setText("of " + total + " bays · " + percent(stats.available(), total) + "%");
        entriesValue.setText(Integer.toString(stats.totalEntries()));
        entriesHint.setText("Cars " + stats.carsEntered() + " · Bikes " + stats.bikesEntered());
        exitsValue.setText(Integer.toString(stats.totalExits()));
        exitsHint.setText("Cars " + stats.carsExited() + " · Bikes " + stats.bikesExited());

        carsSummaryLabel.setText(stats.carsEntered() + " entered · " + stats.carsExited() + " exited");
        bikesSummaryLabel.setText(stats.bikesEntered() + " entered · " + stats.bikesExited() + " exited");
        evUsageLabel.setText(stats.evInUse() + " / " + stats.evTotal() + " in use");
        evUsageBar.setProgress(stats.evTotal() == 0 ? 0 : (double) stats.evInUse() / stats.evTotal());
        peakHourValue.setText(stats.peakHour());
        peakHint.setText(stats.peakHourEntries() + " entries");
    }

    private int percent(int value, int total) {
        return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }

    private void buildFloorGrid(GridPane grid, String floorCode) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int i = 0; i < 6; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 6);
            column.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        List<PreviewBay> bays = PreviewData.baysForFloor(floorCode);
        for (int index = 0; index < bays.size(); index++) {
            PreviewBay bay = bays.get(index);
            grid.add(createBayTile(bay), index % 6, index / 6);
        }
    }

    private ToggleButton createBayTile(PreviewBay bay) {
        Label id = new Label(bay.id());
        id.getStyleClass().add("bay-id");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox top = new HBox(6, id, spacer);
        top.setAlignment(Pos.CENTER_LEFT);

        if (bay.charging()) {
            Label ev = new Label("EV");
            ev.getStyleClass().add("bay-ev-tag");
            HBox evBadge = new HBox(3, Icons.bolt(9, "icon-blue"), ev);
            evBadge.setAlignment(Pos.CENTER);
            evBadge.getStyleClass().add("bay-ev-badge");
            top.getChildren().add(evBadge);
        }

        Label primary = new Label(bay.occupied() ? bay.registration() : "Available");
        primary.getStyleClass().add(bay.occupied() ? "bay-plate" : "bay-available");
        Label secondary = new Label("Zone " + bay.zone() + " · " + (bay.occupied()
                ? (bay.vehicleType() == VehicleType.BIKE ? "Bike" : "Car") + " · Occupied"
                : (bay.charging() ? "Charging bay · Free" : "Standard bay · Free")));
        secondary.getStyleClass().add("bay-sub");
        primary.setMinWidth(0);
        primary.setMaxWidth(Double.MAX_VALUE);
        secondary.setMinWidth(0);
        secondary.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(2, top, primary, secondary);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setFillWidth(true);

        ToggleButton tile = new ToggleButton();
        tile.getStyleClass().addAll("bay-tile", bay.occupied() ? "is-occupied" : "is-available");
        tile.setToggleGroup(bayGroup);
        tile.setGraphic(content);
        tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        tile.setUserData(bay);
        tile.setAccessibleText(bay.id() + ", " + bay.statusText()
                + (bay.occupied() ? ", " + bay.registration() : "")
                + (bay.charging() ? ", EV charging" : ""));
        tile.setTooltip(new Tooltip(bay.id() + " · " + bay.statusText()
                + (bay.occupied() ? "\n" + bay.registration() + " · " + bay.vehicleType().displayName() : "")
                + (bay.charging() ? "\nEV charging bay" : "")));
        return tile;
    }

    private void showBayDetails(PreviewBay bay) {
        bayDetailStatus.getStyleClass().removeAll("is-free", "is-occupied");
        if (bay == null) {
            bayDetailTitle.setText("Select a bay");
            bayDetailStatus.setText("Available");
            bayDetailStatus.getStyleClass().add("is-free");
            bayDetailMeta.setText("Tap any bay to inspect it.");
            bayDetailSession.setText("Vehicle information appears here.");
            return;
        }
        bayDetailTitle.setText(bay.id() + " · " + bay.floorName());
        bayDetailStatus.setText(bay.statusText());
        bayDetailStatus.getStyleClass().add(bay.occupied() ? "is-occupied" : "is-free");
        bayDetailMeta.setText("Zone " + bay.zone() + (bay.charging() ? " · Charging bay" : " · Standard bay"));
        if (bay.occupied()) {
            bayDetailSession.setText(bay.registration() + " · " + bay.vehicleType().displayName()
                    + "\nEntered " + FormatUtil.shortDateTime(bay.entryTime()));
        } else {
            bayDetailSession.setText("No active vehicle. Ready for the next arrival.");
        }
    }

    private void populateAlerts() {
        List<PreviewLongStay> alerts = PreviewData.longStayAlerts();
        longStayCount.setText(Integer.toString(alerts.size()));
        alertsList.getChildren().clear();
        if (alerts.isEmpty()) {
            Label empty = new Label("No long-stay vehicles.");
            empty.getStyleClass().add("empty-state");
            alertsList.getChildren().add(empty);
            return;
        }
        for (PreviewLongStay alert : alerts) {
            Label plate = new Label(alert.registration());
            plate.getStyleClass().add("alert-plate");
            Label duration = new Label(FormatUtil.duration(alert.elapsed()));
            duration.getStyleClass().add("alert-duration");
            HBox lineOne = new HBox(plate, new Region(), duration);
            HBox.setHgrow(lineOne.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
            lineOne.setAlignment(Pos.CENTER_LEFT);

            Label meta = new Label(alert.bay() + " · " + alert.floorName());
            meta.getStyleClass().add("alert-meta");
            Label entered = new Label("Entered " + FormatUtil.shortDateTime(alert.entryTime()));
            entered.getStyleClass().add("alert-meta");
            VBox row = new VBox(3, lineOne, meta, entered);
            row.getStyleClass().add("alert-row");
            alertsList.getChildren().add(row);
        }
    }

    @FXML
    private void handleEntryKiosk() {
        if (navigator != null) {
            navigator.showEntry();
        }
    }

    @FXML
    private void handleExitKiosk() {
        if (navigator != null) {
            navigator.showExit();
        }
    }

    @FXML
    private void handleLock() {
        if (navigator != null) {
            navigator.showKiosk();
        }
    }

    @FXML
    private void showResetDialog() {
        resetOverlay.setVisible(true);
        resetOverlay.setManaged(true);
        resetOverlay.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(150), resetOverlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        cancelResetButton.requestFocus();
    }

    @FXML
    private void cancelReset() {
        resetOverlay.setVisible(false);
        resetOverlay.setManaged(false);
        resetButton.requestFocus();
    }

    @FXML
    private void confirmReset() {
        resetOverlay.setVisible(false);
        resetOverlay.setManaged(false);
        previewNotice.setText("Preview only — reset is not implemented in Phase 1. No sessions, bays or records were changed.");
        previewNotice.setManaged(true);
        previewNotice.setVisible(true);
        workspaceScroll.setVvalue(0);
        resetButton.requestFocus();
    }

    @FXML
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && resetOverlay.isVisible()) {
            cancelReset();
            event.consume();
        }
    }
}
