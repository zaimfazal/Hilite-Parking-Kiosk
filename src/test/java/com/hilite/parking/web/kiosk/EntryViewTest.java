package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ParkingService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ViewPackages(packages = "com.hilite.parking.web")
class EntryViewTest extends SpringBrowserlessTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("entry-view.db").toString());
    }

    @Autowired
    DemoResetService demoReset;

    @Autowired
    ParkingService parkingService;

    @Autowired
    ParkingSessionRepository sessions;

    @BeforeEach
    void cleanDatabase() {
        demoReset.reset();
    }

    @Test
    void controlsStayLockedUntilTheSimulatedScanCompletes() {
        EntryView view = navigate(EntryView.class);

        assertFalse(view.plateField.isEnabled());
        assertFalse(view.findButton.isEnabled());
        assertEquals("Scanning number plate…", view.scanCard.titleText());

        view.completeScan();

        assertTrue(view.plateField.isEnabled());
        assertTrue(view.findButton.isEnabled());
        assertEquals(EntryView.DEMO_SCAN_PLATE, view.plateField.getValue());
        assertEquals("Number plate ready", view.scanCard.titleText());
        assertEquals("KL 11 AB 1234", view.scanCard.previewText());
    }

    @Test
    void happyPathAllocatesG01AndNavigatesToTheAllocationPage() {
        EntryView view = navigate(EntryView.class);
        view.completeScan();

        test(view.findButton).click();

        List<ParkingSession> active = sessions.findActive();
        assertEquals(1, active.size());
        AllocationView allocation = assertInstanceOf(AllocationView.class, getCurrentView());
        assertEquals("G-01", allocation.bayCode.getText());
        assertEquals("G-01", active.get(0).slotId());
    }

    @Test
    void duplicatePlateShowsInlineErrorAndCreatesNoSecondSession() {
        AllocationOutcome first = parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        assertInstanceOf(AllocationOutcome.Allocated.class, first);

        EntryView view = navigate(EntryView.class);
        view.completeScan();
        test(view.findButton).click();

        assertInstanceOf(EntryView.class, getCurrentView());
        assertTrue(view.error.isShowing());
        assertEquals("This vehicle already has an active parking session.", view.error.getText());
        assertEquals("alert", view.error.getElement().getAttribute("role"));
        assertEquals(1, sessions.findAll().size());
        // Controls are usable again after the refusal.
        assertTrue(view.findButton.isEnabled());
        assertEquals(EntryView.FIND_LABEL, view.findButton.getLabel());
    }

    @Test
    void blankPlateIsRejectedInlineWithoutCallingTheService() {
        EntryView view = navigate(EntryView.class);
        view.completeScan();
        test(view.plateField).setValue("");

        test(view.findButton).click();

        assertEquals("Enter your vehicle number plate to continue.", view.error.getText());
        assertEquals(0, sessions.findAll().size());
    }

    @Test
    void bikeWithChargingIsSentToTheService() {
        EntryView view = navigate(EntryView.class);
        view.completeScan();
        test(view.bikeCard).click();
        test(view.evYes).click();
        assertEquals("true", view.bikeCard.getElement().getAttribute("aria-pressed"));
        assertEquals("false", view.carCard.getElement().getAttribute("aria-pressed"));

        test(view.findButton).click();

        ParkingSession session = sessions.findActive().get(0);
        assertEquals(VehicleType.BIKE, session.vehicleType());
        assertTrue(session.chargingRequested());
        assertEquals("G-03", session.slotId());
    }
}
