package com.hilite.parking.web.kiosk;

import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.ExitOutcome;
import com.hilite.parking.service.ExitQuote;
import com.hilite.parking.service.ExitService;
import com.hilite.parking.service.ExitSummary;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.web.ui.ActionButton;
import com.hilite.parking.web.ui.Icons;
import com.hilite.parking.web.ui.Plates;
import com.hilite.parking.web.ui.InlineMessage;
import com.hilite.parking.web.ui.KioskPage;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vehicle Exit kiosk with four sequential states in one view: scan → session &amp; fee → demo
 * payment → success.
 *
 * <p>The view keeps the single {@link ExitQuote} it displayed and passes that same quote back
 * on confirmation, so the amount charged is always the amount shown. Calculating, opening the
 * payment panel and cancelling never write; only {@link ExitService#confirmPayment} and
 * {@link ExitService#completeZeroFee} close a session. A busy flag plus disabled buttons make
 * sure each click causes at most one service call. A browser refresh rebuilds the view at the
 * scan state and writes nothing.</p>
 */
@Route("exit")
@PageTitle("Vehicle Exit · HiLITE Mall")
public class ExitView extends KioskPage {

    static final int SCAN_MILLIS = 1950;
    static final String DEMO_SCAN_PLATE = "KL 11 AB 1234";
    static final String MESSAGE_NOT_READY = "The exit kiosk is not ready yet. Please try again.";
    static final String CALCULATE_LABEL = "Calculate Parking Fee";
    static final String CONFIRM_LABEL = "Confirm Demo Payment";

    private final ExitService exitService;
    private final ZoneId zone;

    // State A: scan
    final Div scanState = new Div();
    final ScanCard scanCard = new ScanCard(SCAN_MILLIS, "exit-scan-card");
    final PlateInput plateField = new PlateInput();
    final InlineMessage scanError = new InlineMessage(InlineMessage.Kind.ERROR);
    final ActionButton calculateButton =
            new ActionButton(CALCULATE_LABEL, Icons.arrowRight(19), "btn-primary", "kiosk-cta");

    // State B: session and fee
    final Div detailsState = new Div();
    final Span detailPlate = text("", "session-plate");
    final Span detailType = text("", "field-hint");
    final Span entryValue = text("", "fee-value");
    final Span exitValue = text("", "fee-value");
    final Span durationValue = text("", "fee-value strong");
    final Span freeValue = text("", "fee-value");
    final Span chargeableValue = text("", "fee-value strong");
    final Span rateValue = text("", "fee-value");
    final Span feeValue = text("", "amount-value");
    final InlineMessage detailsNotice = new InlineMessage(InlineMessage.Kind.INFO);
    final ActionButton backButton =
            new ActionButton("Back", Icons.arrowLeft(17), "btn-secondary", "kiosk-cta");
    final ActionButton payButton =
            new ActionButton("Pay & Exit", Icons.arrowRight(19), "btn-primary", "kiosk-cta");

    // State C: demo payment
    final Div paymentState = new Div();
    final Span paymentSummary = text("", "field-hint");
    final Span paymentAmount = text("", "payment-amount");
    final InlineMessage paymentError = new InlineMessage(InlineMessage.Kind.ERROR);
    final ActionButton cancelPaymentButton =
            new ActionButton("Cancel", null, "btn-secondary", "kiosk-cta");
    final ActionButton confirmPaymentButton =
            new ActionButton(CONFIRM_LABEL, Icons.card(19), "btn-primary", "kiosk-cta");

    // State D: success
    final Div successState = new Div();
    final H1 successTitle = new H1("Payment successful");
    final Span successCaption = text("AMOUNT PAID", "detail-label");
    final Span successAmount = text("", "payment-amount");
    final Span successHint = text("Demo payment confirmed", "field-hint");
    final Span successVehicle = text("", "fee-value");
    final Span successEntry = text("", "fee-value");
    final Span successExit = text("", "fee-value");
    final Span successDuration = text("", "fee-value");
    final ActionButton successDoneButton =
            new ActionButton("Done", Icons.arrowRight(19), "btn-primary", "kiosk-cta");

    private final List<Div> states = List.of(scanState, detailsState, paymentState, successState);
    private ExitQuote quote;
    private boolean scanComplete;
    private boolean busy;
    private long scanStartedNanos;

    public ExitView(ExitService exitService, Clock clock, ZoneId mallTimeZone) {
        super(clock, mallTimeZone,
                "Demonstration interface — simulated number-plate scanning and payment.");
        this.exitService = exitService;
        this.zone = mallTimeZone;
        addClassName("exit-view");
        header().setTitle("VEHICLE EXIT");
        body().addClassName("exit-body");

        buildScanState();
        buildDetailsState();
        buildPaymentState();
        buildSuccessState();

        Div panel = box("glass-panel exit-panel", scanState, detailsState, paymentState, successState);
        body().add(hero(), panel);
        showState(scanState, false);
    }

    private Component hero() {
        H2 title = new H2("HiLITE MALL");
        title.addClassNames("hero-title", "exit-hero-title");
        Paragraph sub = new Paragraph("A smoother exit, before you reach the ramp.");
        sub.addClassName("hero-sub");
        Div reassurance = box("exit-reassurance",
                box("scan-icon-tile", Icons.clock(21)),
                box("scan-copy", text("Exit kiosk", "scan-title"),
                        text("Scan or type your plate to calculate your parking fee.", "scan-subtitle")));
        return box("kiosk-hero exit-hero", text("THANK YOU FOR VISITING", "hero-eyebrow"),
                title, sub, reassurance);
    }

    private void buildScanState() {
        H1 title = new H1("Vehicle Exit");
        title.addClassName("panel-title");
        Paragraph sub = new Paragraph("Scan your number plate to find your parking session.");
        sub.addClassName("panel-subtitle");
        calculateButton.setEnabled(false);
        calculateButton.addClickListener(event -> handleCalculate());
        plateField.addValueChangeListener(event -> {
            scanCard.showPlate(event.getValue());
            scanError.hide();
        });
        scanState.addClassName("exit-state");
        scanState.add(EntryView.stepRow("01", "VEHICLE EXIT"), title, sub, scanCard, plateField,
                scanError, box("panel-spacer"), calculateButton,
                note("Calculating the fee does not close your session or release your bay."));
    }

    private void buildDetailsState() {
        H1 title = new H1("Your parking session");
        title.addClassName("panel-title");
        Div table = box("fee-table",
                feeRow("Entry time", entryValue),
                feeRow("Exit quote time", exitValue),
                feeRow("Total duration", durationValue),
                box("fee-divider"),
                feeRow("Free allowance", freeValue),
                feeRow("Chargeable duration", chargeableValue),
                feeRow("Hourly rate", rateValue));
        Div amount = box("amount-due",
                box("amount-copy", text("Amount due", "amount-label"),
                        text("Including all applicable hours", "amount-hint")),
                feeValue);
        backButton.addClickListener(event -> handleBackToScan());
        payButton.addClickListener(event -> handlePay());
        detailsState.addClassName("exit-state");
        detailsState.add(EntryView.stepRow("02", "SESSION & FEE"), title, detailPlate, detailType,
                table, amount, detailsNotice, box("panel-spacer"),
                box("action-pair", backButton, payButton),
                note("Calculated from your saved entry time. Your session stays open until you complete the exit."));
    }

    private void buildPaymentState() {
        H1 title = new H1("Demo payment");
        title.addClassName("panel-title");
        Paragraph sub = new Paragraph("Confirm the demo payment to close your parking session.");
        sub.addClassName("panel-subtitle");
        Div amountBlock = box("payment-amount-block",
                text("AMOUNT DUE", "detail-label"), paymentAmount,
                box("payment-demo-line", box("detail-icon", Icons.card(18)),
                        text("Demo payment · no real charge", "field-hint")));
        cancelPaymentButton.addClickListener(event -> handleCancelPayment());
        confirmPaymentButton.addClickListener(event -> handleConfirmPayment());
        paymentState.addClassName("exit-state");
        paymentState.add(EntryView.stepRow("03", "DEMO PAYMENT"), title, sub, paymentSummary,
                box("panel-spacer"), amountBlock,
                note("Demonstration only — no real money is collected. No gateway, card, UPI or bank connection is used."),
                paymentError, box("panel-spacer"),
                box("action-pair", cancelPaymentButton, confirmPaymentButton));
    }

    private void buildSuccessState() {
        successTitle.addClassName("allocation-title");
        Div head = box("success-row", box("success-badge", Icons.check(30)),
                box("success-copy", successTitle,
                        text("Your parking session is now closed.", "allocation-subtitle")));
        Div amountBlock = box("payment-amount-block", successCaption, successAmount, successHint);
        Span status = text("Parking Session Closed", "status-closed");
        Div table = box("fee-table success-table",
                feeRow("Vehicle", successVehicle),
                feeRow("Entry", successEntry),
                feeRow("Exit", successExit),
                feeRow("Duration", successDuration),
                feeRow("Status", status));
        successDoneButton.addClickListener(event -> handleDone());
        successState.addClassName("exit-state");
        successState.add(head, amountBlock, table, box("panel-spacer"), successDoneButton,
                note("Demonstration only — no physical barrier was opened."));
    }

    private static Div feeRow(String label, Component value) {
        return box("fee-row", text(label, "fee-label"), value);
    }

    private static Paragraph note(String text) {
        Paragraph paragraph = new Paragraph(text);
        paragraph.addClassName("panel-note");
        return paragraph;
    }

    // ---- scan ------------------------------------------------------------------------------

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
        scanCard.complete("Review the plate, then calculate your fee.", DEMO_SCAN_PLATE);
        plateField.setEnabled(true);
        plateField.setValue(DEMO_SCAN_PLATE);
        plateField.focus();
        calculateButton.setEnabled(true);
    }

    // ---- State A → B -----------------------------------------------------------------------

    void handleCalculate() {
        if (busy) {
            return;
        }
        if (!scanComplete) {
            scanError.show("Please wait for the simulated scan to finish.");
            return;
        }
        String rawPlate = plateField.getValue();
        if (rawPlate == null || rawPlate.isBlank()) {
            scanError.show("Enter your vehicle number plate to calculate the fee.");
            plateField.focus();
            return;
        }
        busy = true;
        scanError.hide();
        plateField.setEnabled(false);
        calculateButton.showBusy("Calculating your fee…");
        call(() -> exitService.quote(rawPlate), outcome -> {
            restoreScanControls();
            if (outcome instanceof ExitOutcome.Quoted quoted) {
                quote = quoted.quote();
                populateDetails();
                showState(detailsState, true);
            } else if (outcome instanceof ExitOutcome.Rejected rejected) {
                scanError.show(rejected.message());
            } else {
                scanError.show(MESSAGE_NOT_READY);
            }
        }, () -> {
            restoreScanControls();
            scanError.show("Could not read your parking session. Please try again.");
        });
    }

    private void restoreScanControls() {
        busy = false;
        plateField.setEnabled(true);
        calculateButton.restore(CALCULATE_LABEL, Icons.arrowRight(19));
    }

    // ---- State B ---------------------------------------------------------------------------

    private void populateDetails() {
        detailPlate.setText(Plates.display(quote.registration()));
        detailType.setText(quote.vehicleType().displayName() + " · Bay " + quote.slotId() + ", "
                + quote.floor().displayName());
        entryValue.setText(FormatUtil.dateTime(quote.entryTime(), zone));
        exitValue.setText(FormatUtil.dateTime(quote.exitTime(), zone));
        durationValue.setText(FormatUtil.duration(quote.totalDuration()));
        freeValue.setText("First " + quote.freeAllowance().toMinutes() + " minutes free");
        chargeableValue.setText(quote.chargeableDuration().isZero()
                ? "None"
                : FormatUtil.duration(quote.chargeableDuration()) + " (rounded up)");
        rateValue.setText(FormatUtil.money(quote.hourlyRate()) + " per hour");
        feeValue.setText(FormatUtil.money(quote.amountDue()));
        detailsNotice.hide();
        restoreDetailControls();
    }

    private String payLabel() {
        return quote != null && quote.paymentRequired() ? "Pay & Exit" : "Complete Exit";
    }

    private void restoreDetailControls() {
        busy = false;
        backButton.setEnabled(true);
        payButton.restore(payLabel(), Icons.arrowRight(19));
    }

    void handleBackToScan() {
        if (busy) {
            return;
        }
        quote = null;
        showState(scanState, true);
    }

    void handlePay() {
        if (busy || quote == null) {
            return;
        }
        if (quote.paymentRequired()) {
            // Opening the payment panel writes nothing; the session stays active.
            paymentAmount.setText(FormatUtil.money(quote.amountDue()));
            paymentSummary.setText(Plates.display(quote.registration()) + " · parked "
                    + FormatUtil.duration(quote.totalDuration()));
            paymentError.hide();
            showState(paymentState, true);
            return;
        }
        ExitQuote freeQuote = quote;
        busy = true;
        detailsNotice.hide();
        backButton.setEnabled(false);
        payButton.showBusy("Closing your session…");
        call(() -> exitService.completeZeroFee(freeQuote),
                outcome -> handleCompletion(outcome, this::restoreDetailControls,
                        message -> detailsNotice.show(InlineMessage.Kind.ERROR, message)),
                () -> {
                    restoreDetailControls();
                    detailsNotice.show(InlineMessage.Kind.ERROR,
                            "Could not close your parking session. It is still open — please try again.");
                });
    }

    // ---- State C ---------------------------------------------------------------------------

    void handleCancelPayment() {
        if (busy) {
            return;
        }
        // Nothing has been written: the session is still active and the bay stays occupied.
        showState(detailsState, true);
        detailsNotice.show(InlineMessage.Kind.INFO,
                "Payment cancelled. Your parking session is still open and no charge was made.");
    }

    void handleConfirmPayment() {
        if (busy || quote == null) {
            return;
        }
        ExitQuote paidQuote = quote;
        busy = true;
        paymentError.hide();
        cancelPaymentButton.setEnabled(false);
        confirmPaymentButton.showBusy("Confirming…");
        call(() -> exitService.confirmPayment(paidQuote),
                outcome -> handleCompletion(outcome, this::restorePaymentControls, paymentError::show),
                () -> {
                    restorePaymentControls();
                    paymentError.show("Could not record the demo payment. Nothing was charged and "
                            + "your session is still open — please try again.");
                });
    }

    private void restorePaymentControls() {
        busy = false;
        cancelPaymentButton.setEnabled(true);
        confirmPaymentButton.restore(CONFIRM_LABEL, Icons.card(19));
    }

    /**
     * Shows success only for a saved completion. A quote that is no longer valid sends the kiosk
     * back to the scan state with the reason, so the old amount can never be retried.
     */
    private void handleCompletion(ExitOutcome outcome, Runnable restore, Consumer<String> showInPlace) {
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
                    scanError.show(rejected.message());
                }
                default -> showInPlace.accept(rejected.message());
            }
        } else {
            showInPlace.accept(MESSAGE_NOT_READY);
        }
    }

    // ---- State D ---------------------------------------------------------------------------

    private void showSuccess(ExitSummary summary) {
        quote = null;
        boolean paid = summary.paid();
        successTitle.setText(paid ? "Payment successful" : "No payment required");
        successCaption.setText(paid ? "AMOUNT PAID" : "PARKING FEE");
        successAmount.setText(FormatUtil.money(summary.amountPaid()));
        successHint.setText(paid
                ? "Demo payment confirmed · no real money collected"
                : "Within the free allowance · no payment taken");
        successVehicle.setText(Plates.display(summary.registration()) + " · " + summary.vehicleType().displayName());
        successEntry.setText(FormatUtil.dateTime(summary.entryTime(), zone));
        successExit.setText(FormatUtil.dateTime(summary.exitTime(), zone));
        successDuration.setText(FormatUtil.duration(summary.totalDuration()));
        showState(successState, true);
    }

    void handleDone() {
        // Reload the route so the next driver gets a fresh scan.
        getUI().ifPresent(ui -> ui.getPage().reload());
    }

    // ---- helpers ---------------------------------------------------------------------------

    /**
     * Runs one service call. A {@link DataAccessException} is an unexpected persistence
     * failure and is reported in place, never as a parking outcome.
     */
    private static void call(Supplier<ExitOutcome> serviceCall, Consumer<ExitOutcome> onOutcome,
                             Runnable onFailure) {
        ExitOutcome outcome;
        try {
            outcome = serviceCall.get();
        } catch (DataAccessException failure) {
            onFailure.run();
            return;
        }
        onOutcome.accept(outcome);
    }

    private void showState(Div state, boolean moveFocus) {
        for (Div candidate : states) {
            boolean active = candidate == state;
            candidate.setVisible(active);
            if (active) {
                candidate.addClassName("is-entering");
            } else {
                candidate.removeClassName("is-entering");
            }
        }
        if (moveFocus) {
            Focusable<?> target = state == scanState ? plateField
                    : state == detailsState ? payButton
                    : state == paymentState ? confirmPaymentButton
                    : successDoneButton;
            target.focus();
        }
    }

    Div visibleState() {
        return states.stream().filter(Component::isVisible).findFirst().orElse(scanState);
    }
}
