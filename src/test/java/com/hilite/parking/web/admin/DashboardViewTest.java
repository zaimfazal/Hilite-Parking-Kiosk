package com.hilite.parking.web.admin;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ParkingService;
import com.hilite.parking.web.kiosk.EntryView;
import com.hilite.parking.web.security.AdminAuthorization;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ViewPackages(packages = "com.hilite.parking.web")
class DashboardViewTest extends SpringBrowserlessTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("dashboard.db").toString());
    }

    @Autowired
    DemoResetService demoReset;

    @Autowired
    ParkingService parkingService;

    @Autowired
    ParkingSessionRepository sessions;

    @Autowired
    AdminAccessService adminAccess;

    @Autowired
    AdminAuthorization authorization;

    @BeforeEach
    void cleanDatabase() {
        demoReset.reset();
    }

    private DashboardView unlockedDashboard() {
        navigate(AdminPinView.class);
        assertEquals(AdminAuthorization.PinAttempt.Status.UNLOCKED,
                authorization.attemptUnlock("1234").status());
        return navigate(DashboardView.class);
    }

    @Test
    void lockedDashboardReroutesToThePinScreen() {
        navigate("admin/dashboard", AdminPinView.class);
        assertInstanceOf(AdminPinView.class, getCurrentView());
    }

    @Test
    void emptyDatabaseShowsHonestEmptyStates() {
        DashboardView view = unlockedDashboard();

        assertEquals("0", view.occupancyValue.getText());
        assertEquals("24", view.availableValue.getText());
        assertEquals("—", view.peakValue.getText());
        assertEquals("No entries yet", view.peakHint.getText());
        assertEquals("0", view.longStayCount.getText());
        assertEquals("Select a bay", view.bayDetailTitle.getText());
        assertFalse(view.bayDetailStatus.isVisible(), "no status pill before a bay is selected");
        assertEquals("0 / 4 in use", view.evUsage.getText());
    }

    @Test
    void selectingAnOccupiedBayShowsItsSession() {
        parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        DashboardView view = unlockedDashboard();

        test(view.bayTiles.get("G-01")).click();

        assertEquals("G-01 · Ground Floor", view.bayDetailTitle.getText());
        assertEquals("Occupied", view.bayDetailStatus.getText());
        assertEquals("true", view.bayTiles.get("G-01").getElement().getAttribute("aria-pressed"));

        view.refresh();
        assertEquals("G-01 · Ground Floor", view.bayDetailTitle.getText(), "selection survives refresh");
    }

    @Test
    void modeSelectorPersistsWithoutNavigating() {
        DashboardView view = unlockedDashboard();

        test(view.exitModeButton).click();

        assertEquals(KioskMode.EXIT, adminAccess.currentMode());
        assertInstanceOf(DashboardView.class, getCurrentView());
        assertEquals("true", view.exitModeButton.getElement().getAttribute("aria-pressed"));
        assertTrue(view.notice.isShowing());
    }

    @Test
    void resetCancelChangesNothing() {
        parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        DashboardView view = unlockedDashboard();

        test(view.resetButton).click();
        assertTrue(view.resetDialog.isOpened());
        test(view.cancelResetButton).click();

        assertFalse(view.resetDialog.isOpened());
        assertEquals(1, sessions.findAll().size());
        assertEquals("1", view.occupancyValue.getText());
    }

    @Test
    void resetConfirmClearsSessionsAndSetsEntryMode() {
        parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        parkingService.allocateParking("KL 07 CD 5678", VehicleType.CAR, false);
        adminAccess.changeMode(KioskMode.EXIT);
        DashboardView view = unlockedDashboard();
        assertEquals("2", view.occupancyValue.getText());

        test(view.resetButton).click();
        test(view.confirmResetButton).click();

        assertFalse(view.resetDialog.isOpened());
        assertTrue(sessions.findAll().isEmpty());
        assertEquals(KioskMode.ENTRY, adminAccess.currentMode());
        assertEquals("0", view.occupancyValue.getText());
        assertTrue(view.notice.getText().startsWith("Demo data reset."));
        assertTrue(authorization.isUnlocked(), "reset keeps the admin signed in");
    }

    @Test
    void lockReturnsToTheKioskAndLocksTheDashboardAgain() {
        DashboardView view = unlockedDashboard();

        test(view.lockButton).click();

        assertInstanceOf(EntryView.class, getCurrentView());
        assertFalse(authorization.isUnlocked());
        navigate("admin/dashboard", AdminPinView.class);
        assertInstanceOf(AdminPinView.class, getCurrentView());
    }
}
