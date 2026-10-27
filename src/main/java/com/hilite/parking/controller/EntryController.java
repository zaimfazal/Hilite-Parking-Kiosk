package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.ParkingService;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.util.GarageBackdrop;
import com.hilite.parking.util.Icons;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/** Vehicle Entry kiosk. Simulates the plate scan, then asks the shared service for a saved bay. */
public class EntryController implements Initializable, NavigatorAware, ServiceAware {

    private static final String DEMO_SCAN_PLATE = "KL 11 AB 1234";
    private static final String PLATE_PLACEHOLDER = "•••• •• ••••";

    @FXML private KioskHeaderController headerController;
    @FXML private StackPane backdropHost;
    @FXML private StackPane scanIconHost;
    @FXML private Label scanTitle;
    @FXML private Label scanSubtitle;
    @FXML private Label platePreview;
    @FXML private Region scanBeam;
    @FXML private StackPane plateFrame;
    @FXML private ProgressBar scanProgress;
    @FXML private ToggleGroup vehicleGroup;
    @FXML private ToggleButton carToggle;
    @FXML private ToggleButton bikeToggle;
    @FXML private ToggleGroup evGroup;
    @FXML private ToggleButton evNoToggle;
    @FXML private ToggleButton evYesToggle;
    @FXML private TextField plateField;
    @FXML private Button clearPlateButton;
    @FXML private Label plateHint;
    @FXML private Label errorLabel;
    @FXML private Button findButton;

    private Navigator navigator;
    private ParkingService parkingService;
    private StackPane carCheck;
    private StackPane bikeCheck;
    private Timeline scanTimeline;
    private TranslateTransition beamAnimation;
    private boolean scanComplete;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        backdropHost.getChildren().add(new GarageBackdrop());
        scanIconHost.getChildren().add(Icons.scan(21, "icon-cyan"));
        configureVehicleCards();
        configureChargingToggle();
        configurePlateField();
        configureActions();
        startSimulatedScan();
    }

    @Override
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        if (headerController != null) {
            headerController.setNavigator(navigator);
            headerController.configure(null);
        }
    }

    /** Supplies the shared service used for the real allocation request. */
    @Override
    public void setParkingService(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    private void configureVehicleCards() {
        carCheck = selectionCheck();
        bikeCheck = selectionCheck();
        carToggle.setGraphic(vehicleGraphic(Icons.car(40, "icon-light"), "Car",
                "Cars, SUVs & hatchbacks", carCheck));
        bikeToggle.setGraphic(vehicleGraphic(Icons.bike(40, "icon-light"), "Bike / Scooter",
                "Two-wheelers & scooters", bikeCheck));
        carToggle.setAccessibleText("Car");
        bikeToggle.setAccessibleText("Bike or scooter");

        vehicleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            carCheck.setVisible(newToggle == carToggle);
            bikeCheck.setVisible(newToggle == bikeToggle);
            hideError();
        });
        carToggle.setSelected(true);
    }

    private StackPane vehicleGraphic(Node icon, String name, String caption, StackPane check) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("vehicle-name");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("vehicle-caption");
        VBox content = new VBox(7, icon, nameLabel, captionLabel);
        content.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(content, check);
        StackPane.setAlignment(check, Pos.TOP_RIGHT);
        StackPane.setMargin(check, new Insets(-2, -2, 0, 0));
        root.setMaxWidth(Double.MAX_VALUE);
        return root;
    }

    private StackPane selectionCheck() {
        StackPane check = new StackPane(Icons.check(12, "icon-white"));
        check.getStyleClass().add("selection-check");
        check.setVisible(false);
        check.setMouseTransparent(true);
        check.setMinSize(21, 21);
        check.setPrefSize(21, 21);
        check.setMaxSize(21, 21);
        return check;
    }

    private void configureChargingToggle() {
        evGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            hideError();
        });
        evNoToggle.setSelected(true);
    }

    private void configurePlateField() {
        plateField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 16 ? change : null));
        clearPlateButton.setGraphic(Icons.close(14, "icon-muted"));
        clearPlateButton.setOnAction(event -> {
            plateField.clear();
            plateField.requestFocus();
            hideError();
        });
        plateField.textProperty().addListener((observable, oldValue, newValue) -> {
            platePreview.setText(newValue == null || newValue.isBlank()
                    ? PLATE_PLACEHOLDER : FormatUtil.normalizePlate(newValue));
            hideError();
        });
        plateField.disableProperty().addListener((observable, wasDisabled, isDisabled) ->
                clearPlateButton.setDisable(isDisabled || plateField.getText().isBlank()));
        plateField.textProperty().addListener((observable, oldValue, newValue) ->
                clearPlateButton.setDisable(plateField.isDisabled() || newValue.isBlank()));
    }

    private void configureActions() {
        findButton.setText("Find My Parking");
        findButton.setGraphic(Icons.arrowRight(19, "icon-white"));
        findButton.setOnAction(event -> handleFindParking());
    }

    private void startSimulatedScan() {
        plateField.setDisable(true);
        findButton.setDisable(true);
        plateHint.setText("The number plate appears here after the simulated scan.");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(plateFrame.widthProperty());
        clip.heightProperty().bind(plateFrame.heightProperty());
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        plateFrame.setClip(clip);

        beamAnimation = new TranslateTransition(Duration.millis(1150), scanBeam);
        beamAnimation.setFromY(-20);
        beamAnimation.setToY(20);
        beamAnimation.setAutoReverse(true);
        beamAnimation.setCycleCount(TranslateTransition.INDEFINITE);
        beamAnimation.play();

        scanTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(scanProgress.progressProperty(), 0)),
                new KeyFrame(Duration.millis(2200), new KeyValue(scanProgress.progressProperty(), 1))
        );
        scanTimeline.setOnFinished(event -> completeScan());
        scanTimeline.play();
    }

    private void completeScan() {
        scanComplete = true;
        if (beamAnimation != null) {
            beamAnimation.stop();
        }
        scanBeam.setVisible(false);
        scanTitle.setText("Number plate ready");
        scanSubtitle.setText("You can edit the scanned plate if needed.");
        scanIconHost.getChildren().add(Icons.check(20, "icon-success"));
        platePreview.setText(DEMO_SCAN_PLATE);
        plateField.setText(DEMO_SCAN_PLATE);
        plateField.setDisable(false);
        plateField.requestFocus();
        plateField.positionCaret(plateField.getText().length());
        clearPlateButton.setDisable(false);
        plateHint.setText("Scanned in this demonstration. Edit the plate if required.");
        findButton.setDisable(false);
        scanProgress.getStyleClass().add("scan-complete");
    }

    @FXML
    private void handleFindParking() {
        if (!scanComplete) {
            showError("Please wait for the simulated scan to finish.");
            return;
        }
        if (parkingService == null) {
            showError("Parking is not ready yet. Please try again.");
            return;
        }
        String rawPlate = plateField.getText();
        if (rawPlate == null || rawPlate.isBlank()) {
            showError("Enter your vehicle number plate to continue.");
            plateField.requestFocus();
            return;
        }
        if (vehicleGroup.getSelectedToggle() == null) {
            showError("Select Car or Bike / Scooter to continue.");
            return;
        }

        VehicleType type = bikeToggle.isSelected() ? VehicleType.BIKE : VehicleType.CAR;
        boolean charging = evYesToggle.isSelected();

        findButton.setDisable(true);
        carToggle.setDisable(true);
        bikeToggle.setDisable(true);
        evNoToggle.setDisable(true);
        evYesToggle.setDisable(true);
        plateField.setDisable(true);
        clearPlateButton.setDisable(true);
        findButton.setText("Finding your bay…");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        spinner.getStyleClass().add("button-spinner");
        findButton.setGraphic(spinner);
        hideError();

        PauseTransition pause = new PauseTransition(Duration.millis(650));
        pause.setOnFinished(event -> {
            try {
                AllocationOutcome outcome = parkingService.allocateParking(rawPlate, type, charging);
                if (outcome instanceof AllocationOutcome.Allocated allocated) {
                    if (navigator != null) {
                        navigator.showAllocation(allocated);
                    } else {
                        restoreControls();
                    }
                } else if (outcome instanceof AllocationOutcome.Rejected rejected) {
                    restoreControls();
                    showError(rejected.message());
                } else {
                    restoreControls();
                    showError("Parking is not ready yet. Please try again.");
                }
            } catch (DataAccessException exception) {
                restoreControls();
                showError("Could not save your parking session. Please try again.");
            }
        });
        pause.play();
    }

    private void restoreControls() {
        carToggle.setDisable(false);
        bikeToggle.setDisable(false);
        evNoToggle.setDisable(false);
        evYesToggle.setDisable(false);
        plateField.setDisable(false);
        clearPlateButton.setDisable(plateField.getText().isBlank());
        findButton.setDisable(false);
        findButton.setText("Find My Parking");
        findButton.setGraphic(Icons.arrowRight(19, "icon-white"));
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
