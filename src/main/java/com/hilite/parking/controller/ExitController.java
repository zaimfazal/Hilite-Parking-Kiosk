package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.ExitOutcome;
import com.hilite.parking.service.ExitQuote;
import com.hilite.parking.service.ExitService;
import com.hilite.parking.service.ExitSummary;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vehicle Exit kiosk with four sequential states: scan → session &amp; fee → demo payment →
 * success.
 *
 * <p>Every value shown comes from the {@link ExitService}. The controller holds the one
 * {@link ExitQuote} it displayed and passes that same quote back on confirmation, so the amount
 * charged is always the amount shown. Calculating a fee, opening the payment panel and
 * cancelling never write anything; only {@link ExitService#confirmPayment} and
 * {@link ExitService#completeZeroFee} close a session. All calls run on the JavaFX Application
 * Thread, and the action buttons are disabled while a call is in progress.</p>
 */
public class ExitController implements Initializable, NavigatorAware, ExitServiceAware {

    private static final String DEMO_SCAN_PLATE = "KL 11 AB 1234";
    private static final String MESSAGE_NOT_READY = "The exit kiosk is not ready yet. Please try again.";

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
    @FXML private Label paymentSummaryLabel;
    @FXML private Label paymentErrorLabel;
    @FXML private StackPane paymentCardIconHost;
    @FXML private Button cancelPaymentButton;
    @FXML private Button confirmPaymentButton;

    @FXML private StackPane successBadge;
    @FXML private Label successTitleLabel;
    @FXML private Label successAmountCaption;
    @FXML private Label successAmountLabel;
    @FXML private Label successAmountHint;
    @FXML private Label successPlateLabel;
    @FXML private Label successEntryLabel;
    @FXML private Label successExitLabel;
    @FXML private Label successDurationLabel;
    @FXML private Button successDoneButton;

    private Navigator navigator;
    private ExitService exitService;
    private ExitQuote quote;
    private boolean scanComplete;
    private boolean busy;
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

    @Override
    public void setExitService(ExitService exitService) {
        this.exitService = exitService;
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
            hide(errorLabel);
            clearPlateButton.setDisable(plateField.isDisabled() || newValue.isBlank());
        });
        clearPlateButton.setOnAction(event -> {
            plateField.clear();
            plateField.requestFocus();
            hide(errorLabel);
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
        platePreview.setText(DEMO_SCAN_PLATE);
        plateField.setText(DEMO_SCAN_PLATE);
        plateField.setDisable(false);
        plateField.requestFocus();
        plateField.positionCaret(plateField.getText().length());
        clearPlateButton.setDisable(false);
        calculateButton.setDisable(false);
        scanProgress.getStyleClass().add("scan-complete");
    }

    // ---- State A: scan → quote -------------------------------------------------------------

    @FXML
    private void handleCalculate() {
        if (busy) {
            return;
        }
        if (!scanComplete) {
            showError("Please wait for the simulated scan to finish.");
            return;
        }
        if (exitService == null) {
            showError(MESSAGE_NOT_READY);
            return;
        }
        String rawPlate = plateField.getText();
        if (rawPlate == null || rawPlate.isBlank()) {
            showError("Enter your vehicle number plate to calculate the fee.");
            plateField.requestFocus();
            return;
        }

        busy = true;
        hide(errorLabel);
        plateField.setDisable(true);
        calculateButton.setDisable(true);
        calculateButton.setText("Calculating your fee…");
        calculateButton.setGraphic(spinner());

        runAfterPause(() -> exitService.quote(rawPlate), outcome -> {
            restoreScanControls();
            if (outcome instanceof ExitOutcome.Quoted quoted) {
                quote = quoted.quote();
                populateDetails();
                showState(detailsState, true);
            } else if (outcome instanceof ExitOutcome.Rejected rejected) {
                showError(rejected.message());
            } else {
                showError(MESSAGE_NOT_READY);
            }
        }, () -> {
            restoreScanControls();
            showError("Could not read your parking session. Please try again.");
        });
    }

    private void restoreScanControls() {
        busy = false;
        plateField.setDisable(false);
        calculateButton.setDisable(false);
        calculateButton.setText("Calculate Parking Fee");
        calculateButton.setGraphic(Icons.arrowRight(19, "icon-white"));
    }

    // ---- State B: session and fee ----------------------------------------------------------

    private void populateDetails() {
        detailPlateLabel.setText(quote.registration());
        detailTypeLabel.setText(quote.vehicleType().displayName() + " · Bay " + quote.slotId()
                + ", " + quote.floor().displayName());
        entryLabel.setText(FormatUtil.dateTime(quote.entryTime()));
        exitLabel.setText(FormatUtil.dateTime(quote.exitTime()));
        durationLabel.setText(FormatUtil.duration(quote.totalDuration()));
        freeLabel.setText("First " + quote.freeAllowance().toMinutes() + " minutes free");
        chargeableLabel.setText(quote.chargeableDuration().isZero()
                ? "None"
                : FormatUtil.duration(quote.chargeableDuration()) + " (rounded up)");
        rateLabel.setText(FormatUtil.money(quote.hourlyRate()) + " per hour");
        feeLabel.setText(FormatUtil.money(quote.amountDue()));
        hide(paymentNotice);
        restoreDetailControls();
    }

    private void restoreDetailControls() {
        busy = false;
        detailBackButton.setDisable(false);
        payButton.setDisable(false);
        payButton.setText(quote != null && quote.paymentRequired() ? "Pay & Exit" : "Complete Exit");
        payButton.setGraphic(Icons.arrowRight(19, "icon-white"));
    }

    @FXML
    private void handleBackToScan() {
        if (busy) {
            return;
        }
        quote = null;
        showState(scanState, true);
    }

    @FXML
    private void handlePay() {
        if (busy || quote == null) {
            return;
        }
        if (quote.paymentRequired()) {
            // Opening the payment panel writes nothing; the session stays active.
            paymentAmountLabel.setText(FormatUtil.money(quote.amountDue()));
            paymentSummaryLabel.setText(quote.registration() + " · parked "
                    + FormatUtil.duration(quote.totalDuration()));
            hide(paymentErrorLabel);
            showState(paymentState, true);
            return;
        }

        ExitQuote freeQuote = quote;
        busy = true;
        hide(paymentNotice);
        detailBackButton.setDisable(true);
        payButton.setDisable(true);
        payButton.setText("Closing your session…");
        payButton.setGraphic(spinner());

        runAfterPause(() -> exitService.completeZeroFee(freeQuote),
                outcome -> handleCompletion(outcome, this::restoreDetailControls,
                        message -> showNotice(message, true)),
                () -> {
                    restoreDetailControls();
                    showNotice("Could not close your parking session. It is still open — please try again.",
                            true);
                });
    }

    // ---- State C: demo payment -------------------------------------------------------------

    @FXML
    private void handleCancelPayment() {
        if (busy) {
            return;
        }
        // Nothing has been written: the session is still active and the bay stays occupied.
        showState(detailsState, true);
        showNotice("Payment cancelled. Your parking session is still open and no charge was made.",
                false);
    }

    @FXML
    private void handleConfirmPayment() {
        if (busy || quote == null) {
            return;
        }
        ExitQuote paidQuote = quote;
        busy = true;
        hide(paymentErrorLabel);
        confirmPaymentButton.setDisable(true);
        cancelPaymentButton.setDisable(true);
        confirmPaymentButton.setText("Confirming…");
        confirmPaymentButton.setGraphic(spinner());

        runAfterPause(() -> exitService.confirmPayment(paidQuote),
                outcome -> handleCompletion(outcome, this::restorePaymentControls,
                        message -> show(paymentErrorLabel, message)),
                () -> {
                    restorePaymentControls();
                    show(paymentErrorLabel,
                            "Could not record the demo payment. Nothing was charged and your session is still open — please try again.");
                });
    }

    private void restorePaymentControls() {
        busy = false;
        confirmPaymentButton.setDisable(false);
        cancelPaymentButton.setDisable(false);
        confirmPaymentButton.setText("Confirm Demo Payment");
        confirmPaymentButton.setGraphic(Icons.card(19, "icon-white"));
    }

    /**
     * Shows success only for a saved completion. A quote that is no longer valid (closed
     * elsewhere, recalculated, unknown) sends the kiosk back to the scan state with the reason,
     * so it can never be retried with the old amount.
     */
    private void handleCompletion(ExitOutcome outcome, Runnable restore,
                                  Consumer<String> showInPlace) {
        if (outcome instanceof ExitOutcome.Completed completed) {
            busy = false;
            showSuccess(completed.summary());
            return;
        }
        restore.run();
        if (outcome instanceof ExitOutcome.Rejected rejected) {
            switch (rejected.reason()) {
                case STALE_QUOTE, ALREADY_COMPLETED, SESSION_NOT_FOUND -> {
                    quote = null;
                    showState(scanState, true);
                    showError(rejected.message());
                }
                default -> showInPlace.accept(rejected.message());
            }
        } else {
            showInPlace.accept(MESSAGE_NOT_READY);
        }
    }

    // ---- State D: success ------------------------------------------------------------------

    private void showSuccess(ExitSummary summary) {
        quote = null;
        boolean paid = summary.paid();
        successTitleLabel.setText(paid ? "Payment successful" : "No payment required");
        successAmountCaption.setText(paid ? "AMOUNT PAID" : "PARKING FEE");
        successAmountLabel.setText(FormatUtil.money(summary.amountPaid()));
        successAmountHint.setText(paid
                ? "Demo payment confirmed · no real money collected"
                : "Within the free allowance · no payment taken");
        successPlateLabel.setText(summary.registration() + " · " + summary.vehicleType().displayName());
        successEntryLabel.setText(FormatUtil.dateTime(summary.entryTime()));
        successExitLabel.setText(FormatUtil.dateTime(summary.exitTime()));
        successDurationLabel.setText(FormatUtil.duration(summary.totalDuration()));
        showState(successState, true);
    }

    @FXML
    private void handleDone() {
        if (navigator != null) {
            navigator.showExit();
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /**
     * Runs one service call after a short visual pause, on the FX thread. A
     * {@link DataAccessException} is an unexpected persistence failure; it is reported in place
     * and never treated as a parking outcome.
     */
    private void runAfterPause(Supplier<ExitOutcome> call,
                               Consumer<ExitOutcome> onOutcome,
                               Runnable onFailure) {
        PauseTransition pause = new PauseTransition(Duration.millis(450));
        pause.setOnFinished(event -> {
            ExitOutcome outcome;
            try {
                outcome = call.get();
            } catch (DataAccessException exception) {
                onFailure.run();
                return;
            }
            onOutcome.accept(outcome);
        });
        pause.play();
    }

    private static ProgressIndicator spinner() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(17, 17);
        spinner.setMaxSize(17, 17);
        spinner.getStyleClass().add("button-spinner");
        return spinner;
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

    private void showNotice(String message, boolean error) {
        paymentNotice.getStyleClass().removeAll("inline-info", "inline-error");
        paymentNotice.getStyleClass().add(error ? "inline-error" : "inline-info");
        show(paymentNotice, message);
    }

    private void showError(String message) {
        show(errorLabel, message);
    }

    private static void show(Label label, String message) {
        label.setText(message);
        label.setManaged(true);
        label.setVisible(true);
    }

    private static void hide(Label label) {
        label.setManaged(false);
        label.setVisible(false);
    }
}
