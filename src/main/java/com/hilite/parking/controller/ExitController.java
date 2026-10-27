package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.preview.PreviewData;
import com.hilite.parking.preview.PreviewExitQuote;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.util.GarageBackdrop;
import com.hilite.parking.util.Icons;
import com.hilite.parking.util.UiEffects;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/** Vehicle Exit kiosk with four sequential presentation states. */
public class ExitController implements Initializable, NavigatorAware {

    @FXML private KioskHeaderController headerController;
    @FXML private StackPane backdropHost;
    @FXML private StackPane heroClockHost;
    @FXML private VBox scanState;
    @FXML private VBox detailsState;
    @FXML private VBox paymentState;
    @FXML private VBox successState;

    @FXML private StackPane scanIconHost;
    @FXML private Label scanTitle;
    @FXML private Label scanSubtitle;
    @FXML private Label platePreview;
    @FXML private Region scanBeam;
    @FXML private StackPane plateFrame;
    @FXML private ProgressBar scanProgress;
    @FXML private TextField plateField;
    @FXML private Button clearPlateButton;
    @FXML private Label errorLabel;
    @FXML private Button calculateButton;

    @FXML private Label detailPlateLabel;
    @FXML private Label detailTypeLabel;
    @FXML private Label entryLabel;
    @FXML private Label exitLabel;
    @FXML private Label durationLabel;
    @FXML private Label freeLabel;
    @FXML private Label chargeableLabel;
    @FXML private Label rateLabel;
    @FXML private Label feeLabel;
    @FXML private Label paymentNotice;
    @FXML private Button detailBackButton;
    @FXML private Button payButton;

    @FXML private Label paymentAmountLabel;
    @FXML private StackPane paymentCardIconHost;
    @FXML private Button cancelPaymentButton;
    @FXML private Button confirmPaymentButton;

    @FXML private StackPane successBadge;
    @FXML private Label successTitleLabel;
    @FXML private Label successAmountLabel;
    @FXML private Label successPlateLabel;
    @FXML private Label successEntryLabel;
    @FXML private Label successExitLabel;
    @FXML private Button successDoneButton;

    private Navigator navigator;
    private PreviewExitQuote quote;
    private boolean scanComplete;
    private Timeline scanTimeline;
    private TranslateTransition beamAnimation;
    private VBox[] states;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        states = new VBox[]{scanState, detailsState, paymentState, successState};
        backdropHost.getChildren().add(new GarageBackdrop());
        heroClockHost.getChildren().add(Icons.clock(21, "icon-cyan"));
        scanIconHost.getChildren().add(Icons.scan(21, "icon-cyan"));
        configureIcons();
        configurePlateField();
        showState(scanState, false);
        startSimulatedScan();
    }

    @Override
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        if (headerController != null) {
            headerController.setNavigator(navigator);
            headerController.configure("VEHICLE EXIT");
        }
    }

    private void configureIcons() {
        calculateButton.setText("Calculate Parking Fee");
        calculateButton.setGraphic(Icons.arrowRight(19, "icon-white"));
        payButton.setGraphic(Icons.arrowRight(19, "icon-white"));
        detailBackButton.setGraphic(Icons.arrowLeft(17, "icon-light"));
        confirmPaymentButton.setText("Confirm Demo Payment");
        confirmPaymentButton.setGraphic(Icons.card(19, "icon-white"));
        successDoneButton.setText("Done");
        successDoneButton.setGraphic(Icons.arrowRight(19, "icon-white"));
        successBadge.getChildren().add(Icons.check(30, "icon-white"));
        paymentCardIconHost.getChildren().add(Icons.card(18, "icon-muted"));
        clearPlateButton.setGraphic(Icons.close(14, "icon-muted"));
    }

    private void configurePlateField() {
        plateField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 16 ? change : null));
        plateField.textProperty().addListener((observable, oldValue, newValue) -> {
            hideError();
            clearPlateButton.setDisable(plateField.isDisabled() || newValue.isBlank());
        });
        clearPlateButton.setOnAction(event -> {
            plateField.clear();
            plateField.requestFocus();
            hideError();
        });
        plateField.disableProperty().addListener((observable, wasDisabled, isDisabled) ->
                clearPlateButton.setDisable(isDisabled || plateField.getText().isBlank()));
    }

    private void startSimulatedScan() {
        plateField.setDisable(true);
        calculateButton.setDisable(true);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(plateFrame.widthProperty());
        clip.heightProperty().bind(plateFrame.heightProperty());
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        plateFrame.setClip(clip);

        beamAnimation = new TranslateTransition(Duration.millis(1050), scanBeam);
        beamAnimation.setFromY(-20);
        beamAnimation.setToY(20);
        beamAnimation.setAutoReverse(true);
        beamAnimation.setCycleCount(TranslateTransition.INDEFINITE);
        beamAnimation.play();

        scanTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(scanProgress.progressProperty(), 0)),
                new KeyFrame(Duration.millis(1950), new KeyValue(scanProgress.progressProperty(), 1))
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
        scanSubtitle.setText("Review the plate, then calculate your fee.");
        scanIconHost.getChildren().add(Icons.check(20, "icon-success"));
        platePreview.setText(PreviewData.EXAMPLE_PLATE);
        plateField.setText(PreviewData.EXAMPLE_PLATE);
        plateField.setDisable(false);
        plateField.requestFocus();
        plateField.positionCaret(plateField.getText().length());
        clearPlateButton.setDisable(false);
        calculateButton.setDisable(false);
        scanProgress.getStyleClass().add("scan-complete");
    }

    @FXML
    private void handleCalculate() {
        if (!scanComplete) {
            showError("Please wait for the simulated scan to finish.");
            return;
        }
        String plate = FormatUtil.normalizePlate(plateField.getText());
        if (plate.isEmpty()) {
            showError("Enter your vehicle number plate to preview the session.");
            plateField.requestFocus();
            return;
        }
        plateField.setText(plate);
        hideError();
        quote = PreviewData.createExitQuote(plate);
        populateDetails();
        showState(detailsState, true);
    }

    private void populateDetails() {
        detailPlateLabel.setText(quote.registration());
        detailTypeLabel.setText(quote.vehicleType().displayName() + " · preview session");
        entryLabel.setText(FormatUtil.dateTime(quote.entryTime()));
        exitLabel.setText(FormatUtil.dateTime(quote.exitTime()));
        durationLabel.setText(FormatUtil.duration(quote.duration()));
        freeLabel.setText(FormatUtil.duration(quote.freeAllowance()) + " included");
        chargeableLabel.setText(FormatUtil.duration(quote.chargeableDuration()) + " (rounded up)");
        rateLabel.setText(quote.hourlyRateLabel());
        feeLabel.setText(FormatUtil.money(quote.amountDue()));
        paymentNotice.setVisible(false);
        paymentNotice.setManaged(false);
        payButton.setText(quote.paymentRequired() ? "Pay & Exit" : "Complete Exit");
    }

    @FXML
    private void handleBackToScan() {
        showState(scanState, true);
    }

    @FXML
    private void handlePay() {
        if (quote == null) {
            return;
        }
        if (quote.paymentRequired()) {
            paymentAmountLabel.setText(FormatUtil.money(quote.amountDue()));
            showState(paymentState, true);
        } else {
            showSuccess();
        }
    }

    @FXML
    private void handleCancelPayment() {
        showState(detailsState, true);
        paymentNotice.setText("Payment cancelled. This preview session remains open — no charge was made.");
        paymentNotice.setManaged(true);
        paymentNotice.setVisible(true);
    }

    @FXML
    private void handleConfirmPayment() {
        confirmPaymentButton.setDisable(true);
        cancelPaymentButton.setDisable(true);
        confirmPaymentButton.setText("Confirming…");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(17, 17);
        spinner.setMaxSize(17, 17);
        spinner.getStyleClass().add("button-spinner");
        confirmPaymentButton.setGraphic(spinner);

        PauseTransition pause = new PauseTransition(Duration.millis(600));
        pause.setOnFinished(event -> {
            confirmPaymentButton.setDisable(false);
            cancelPaymentButton.setDisable(false);
            confirmPaymentButton.setText("Confirm Demo Payment");
            confirmPaymentButton.setGraphic(Icons.card(19, "icon-white"));
            showSuccess();
        });
        pause.play();
    }

    private void showSuccess() {
        boolean paid = quote.paymentRequired();
        successTitleLabel.setText(paid ? "Payment successful" : "No payment required");
        successAmountLabel.setText(FormatUtil.money(quote.amountDue()));
        successPlateLabel.setText(quote.registration() + " · " + quote.vehicleType().displayName());
        successEntryLabel.setText(FormatUtil.dateTime(quote.entryTime()));
        successExitLabel.setText(FormatUtil.dateTime(quote.exitTime()));
        showState(successState, true);
    }

    @FXML
    private void handleDone() {
        if (navigator != null) {
            navigator.showExit();
        }
    }

    private void showState(VBox state, boolean animate) {
        for (VBox candidate : states) {
            boolean active = candidate == state;
            candidate.setVisible(active);
            candidate.setManaged(active);
        }
        if (animate) {
            UiEffects.fadeIn(state, Duration.millis(170));
        }
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
