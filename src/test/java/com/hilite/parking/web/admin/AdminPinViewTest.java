package com.hilite.parking.web.admin;

import com.hilite.parking.model.VehicleType;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ParkingService;
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
class AdminPinViewTest extends SpringBrowserlessTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("admin-pin.db").toString());
    }

    @Autowired
    DemoResetService demoReset;

    @Autowired
    ParkingService parkingService;

    @Autowired
    AdminAuthorization authorization;

    @BeforeEach
    void cleanDatabase() {
        demoReset.reset();
    }

    private void enter(AdminPinView view, String digits) {
        for (char digit : digits.toCharArray()) {
            view.appendDigit(String.valueOf(digit));
        }
    }

    @Test
    void wrongPinShowsErrorAndStaysOnThePinScreen() {
        AdminPinView view = navigate(AdminPinView.class);
        enter(view, "0000");

        test(view.unlockButton).click();

        assertInstanceOf(AdminPinView.class, getCurrentView());
        assertTrue(view.error.isShowing());
        assertEquals(AdminPinView.INVALID_PIN, view.error.getText());
        assertEquals("", view.pinField.getValue());
        assertFalse(authorization.isUnlocked());
    }

    @Test
    void keypadCapsTheEntryAtFourDigits() {
        AdminPinView view = navigate(AdminPinView.class);
        enter(view, "123456");
        assertEquals("1234", view.pinField.getValue());
        view.handleBackspace();
        assertEquals("123", view.pinField.getValue());
        view.handleClear();
        assertEquals("", view.pinField.getValue());
    }

    @Test
    void correctPinOpensTheDashboardWithLiveCounts() {
        parkingService.allocateParking("KL 11 AB 1234", VehicleType.CAR, false);
        parkingService.allocateParking("KL 11 XY 4321", VehicleType.BIKE, true);
        AdminPinView view = navigate(AdminPinView.class);
        enter(view, "1234");

        test(view.unlockButton).click();

        DashboardView dashboard = assertInstanceOf(DashboardView.class, getCurrentView());
        assertEquals("2", dashboard.occupancyValue.getText());
        assertEquals("22", dashboard.availableValue.getText());
        assertEquals("2", dashboard.entriesValue.getText());
        assertEquals("0", dashboard.exitsValue.getText());
        assertEquals("Cars 1 · Bikes 1", dashboard.entriesHint.getText());
        assertEquals(24, dashboard.bayTiles.size());
    }

    @Test
    void repeatedWrongPinsLockTheScreenTemporarily() {
        AdminPinView view = navigate(AdminPinView.class);
        for (int attempt = 0; attempt < 5; attempt++) {
            enter(view, "9999");
            test(view.unlockButton).click();
        }
        assertTrue(view.error.getText().startsWith("Too many incorrect PINs."));

        enter(view, "1234");
        test(view.unlockButton).click();

        assertInstanceOf(AdminPinView.class, getCurrentView());
        assertFalse(authorization.isUnlocked());
    }

    @Test
    void returnButtonGoesBackToTheKiosk() {
        AdminPinView view = navigate(AdminPinView.class);
        test(view.returnButton).click();
        assertInstanceOf(com.hilite.parking.web.kiosk.EntryView.class, getCurrentView());
    }
}
