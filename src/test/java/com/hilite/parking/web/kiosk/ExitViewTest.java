package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ParkingService;
import com.hilite.parking.web.ui.MutableTestClock;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ViewPackages(packages = "com.hilite.parking.web")
class ExitViewTest extends SpringBrowserlessTest {

    private static final Instant START = Instant.parse("2026-06-24T04:30:00Z");
    private static final MutableTestClock CLOCK = new MutableTestClock(START);
    private static final String PLATE = "KL 11 AB 1234";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("exit-view.db").toString());
    }

    @TestBean(name = "clock", methodName = "testClock")
    Clock clock;

    static Clock testClock() {
        return CLOCK;
    }

    @Autowired
    DemoResetService demoReset;

    @Autowired
    ParkingService parkingService;

    @Autowired
    ParkingSessionRepository sessions;

    @Autowired
    Database database;

    @BeforeEach
    void cleanDatabase() {
        demoReset.reset();
        CLOCK.set(START);
    }

    private long payments() {
        return new JdbcPaymentRepository(database).findAll().size();
    }

    private void park(Duration parkedFor) {
        assertInstanceOf(AllocationOutcome.Allocated.class,
                parkingService.allocateParking(PLATE, VehicleType.CAR, false));
        CLOCK.advance(parkedFor);
    }

    private ExitView scannedExit() {
        ExitView view = navigate(ExitView.class);
        assertFalse(view.calculateButton.isEnabled());
        view.completeScan();
        assertEquals(PLATE, view.plateField.getValue());
        return view;
    }

    @Test
    void paidExitGoesScanQuotePaySuccessWithExactlyOnePayment() {
        park(Duration.ofHours(4).plusMinutes(24));
        ExitView view = scannedExit();

        test(view.calculateButton).click();
        assertSame(view.detailsState, view.visibleState());
        assertEquals("₹15", view.feeValue.getText());
        assertEquals("4 h 24 min", view.durationValue.getText());
        assertEquals("3 h (rounded up)", view.chargeableValue.getText());
        assertEquals("Pay & Exit", view.payButton.getLabel());
        assertEquals("24 Jun 2026, 10:00 AM", view.entryValue.getText());

        test(view.payButton).click();
        assertSame(view.paymentState, view.visibleState());
        assertEquals("₹15", view.paymentAmount.getText());
        assertEquals(0, payments(), "opening the payment panel writes nothing");

        test(view.confirmPaymentButton).click();
        assertSame(view.successState, view.visibleState());
        assertEquals("Payment successful", view.successTitle.getText());
        assertEquals("₹15", view.successAmount.getText());
        assertEquals(1, payments());
        assertTrue(sessions.findActive().isEmpty());
    }

    @Test
    void cancellingPaymentKeepsTheSessionActiveAndRecordsNoPayment() {
        park(Duration.ofHours(3));
        ExitView view = scannedExit();
        test(view.calculateButton).click();
        test(view.payButton).click();

        test(view.cancelPaymentButton).click();

        assertSame(view.detailsState, view.visibleState());
        assertEquals("Payment cancelled. Your parking session is still open and no charge was made.",
                view.detailsNotice.getText());
        assertEquals(1, sessions.findActive().size());
        assertEquals("G-01", sessions.findActive().get(0).slotId());
        assertEquals(0, payments());
    }

    @Test
    void zeroFeeExitCompletesWithoutAPayment() {
        park(Duration.ofMinutes(45));
        ExitView view = scannedExit();
        test(view.calculateButton).click();
        assertEquals("₹0", view.feeValue.getText());
        assertEquals("Complete Exit", view.payButton.getLabel());
        assertEquals("None", view.chargeableValue.getText());

        test(view.payButton).click();

        assertSame(view.successState, view.visibleState());
        assertEquals("No payment required", view.successTitle.getText());
        assertEquals("PARKING FEE", view.successCaption.getText());
        assertEquals(0, payments());
        assertTrue(sessions.findActive().isEmpty());
    }

    @Test
    void repeatedConfirmCreatesOnlyOnePayment() {
        park(Duration.ofHours(5));
        ExitView view = scannedExit();
        test(view.calculateButton).click();
        test(view.payButton).click();

        test(view.confirmPaymentButton).click();
        // A second click (e.g. a double tap delivered late) must not pay again.
        assertFalse(test(view.confirmPaymentButton).isUsable());
        view.handleConfirmPayment();
        view.handleConfirmPayment();

        assertEquals(1, payments());
        assertSame(view.successState, view.visibleState());
    }

    @Test
    void unknownPlateStaysOnScanWithInlineError() {
        ExitView view = scannedExit();

        test(view.calculateButton).click();

        assertSame(view.scanState, view.visibleState());
        assertEquals("No active parking session was found for this number plate.",
                view.scanError.getText());
        assertTrue(view.calculateButton.isEnabled());
    }

    @Test
    void backFromDetailsReturnsToScanWithoutWriting() {
        park(Duration.ofHours(3));
        ExitView view = scannedExit();
        test(view.calculateButton).click();

        test(view.backButton).click();

        assertSame(view.scanState, view.visibleState());
        assertEquals(1, sessions.findActive().size());
        assertEquals(0, payments());
    }
}
