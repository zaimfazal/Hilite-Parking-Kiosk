package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ExitOutcome;
import com.hilite.parking.service.ExitService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ViewPackages(packages = "com.hilite.parking.web")
class AllocationViewTest extends SpringBrowserlessTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("allocation-view.db").toString());
    }

    @Autowired
    DemoResetService demoReset;

    @Autowired
    ParkingService parkingService;

    @Autowired
    ExitService exitService;

    @Autowired
    ParkingSessionRepository sessions;

    @BeforeEach
    void cleanDatabase() {
        demoReset.reset();
    }

    private AllocationView open(String sessionId) {
        return navigate(AllocationView.class, Map.of(AllocationView.SESSION_PARAM, sessionId));
    }

    @Test
    void unknownSessionShowsNotFoundAndNeverAllocates() {
        AllocationView view = open("no-such-session");

        assertEquals("Parking session not found", view.stateTitle.getText());
        assertEquals("", view.bayCode.getText());
        assertTrue(sessions.findAll().isEmpty());
    }

    @Test
    void revisitingTheAllocationShowsTheSameSavedBay() {
        AllocationOutcome.Allocated allocated = (AllocationOutcome.Allocated)
                parkingService.allocateParking("KL 07 CD 5678", VehicleType.CAR, true);
        String id = allocated.session().sessionId();

        AllocationView first = open(id);
        assertEquals("G-03", first.bayCode.getText());
        assertEquals("Ground Floor", first.floorName.getText());

        AllocationView again = open(id);
        assertEquals("G-03", again.bayCode.getText());
        assertEquals(1, sessions.findAll().size());
    }

    @Test
    void closedSessionSaysSo() {
        AllocationOutcome.Allocated allocated = (AllocationOutcome.Allocated)
                parkingService.allocateParking("KL 11 XY 4321", VehicleType.BIKE, false);
        ExitOutcome.Quoted quote = (ExitOutcome.Quoted) exitService.quote("KL 11 XY 4321");
        assertInstanceOf(ExitOutcome.Completed.class, exitService.completeZeroFee(quote.quote()));

        AllocationView view = open(allocated.session().sessionId());

        assertEquals("This parking session has already been closed", view.stateTitle.getText());
        assertTrue(sessions.findActive().isEmpty());
    }

    @Test
    void ticketDialogOpensAndSimulatesPrint() {
        AllocationOutcome.Allocated allocated = (AllocationOutcome.Allocated)
                parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        AllocationView view = open(allocated.session().sessionId());

        test(view.printButton).click();
        assertTrue(view.ticketDialog.isOpened());
        test(view.simulatePrintButton).click();
        assertEquals("Printed (demo)", view.simulatePrintButton.getLabel());
        assertFalse(view.simulatePrintButton.isEnabled());
        test(view.closeTicketButton).click();
        assertFalse(view.ticketDialog.isOpened());
    }

    @Test
    void doneReturnsToEntry() {
        AllocationOutcome.Allocated allocated = (AllocationOutcome.Allocated)
                parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        AllocationView view = open(allocated.session().sessionId());

        test(view.doneButton).click();

        assertInstanceOf(EntryView.class, getCurrentView());
    }
}
