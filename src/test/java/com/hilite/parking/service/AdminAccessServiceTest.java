package com.hilite.parking.service;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.SettingsRepository;
import com.hilite.parking.repository.jdbc.JdbcSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the demo admin PIN check and the persisted kiosk mode against a real temporary
 * SQLite database.
 */
class AdminAccessServiceTest {

    @TempDir
    Path tempDir;

    private AdminTestSupport.Fixture fixture;
    private AdminAccessService service;

    @BeforeEach
    void setUp() {
        fixture = AdminTestSupport.fixture(tempDir);
        service = new AdminAccessService(fixture.settings);
    }

    // ------------------------------------------------------------------
    // PIN
    // ------------------------------------------------------------------

    @Test
    void defaultSeededPinIsAccepted() {
        assertTrue(service.verifyPin("1234"));
        assertEquals("1234", AdminAccessService.DEFAULT_PIN);
    }

    @Test
    void absentStoredPinFallsBackToDefault() {
        fixture.deleteSetting(SettingsRepository.KEY_ADMIN_PIN);

        assertTrue(service.verifyPin("1234"));
        assertFalse(service.verifyPin("0000"));
    }

    @Test
    void wrongPinIsRejected() {
        assertFalse(service.verifyPin("0000"));
        assertFalse(service.verifyPin("12345"));
        assertFalse(service.verifyPin("123"));
        assertFalse(service.verifyPin("1 234"));
    }

    @Test
    void nullAndBlankPinAreRejected() {
        assertFalse(service.verifyPin(null));
        assertFalse(service.verifyPin(""));
        assertFalse(service.verifyPin("   "));
        assertFalse(service.verifyPin("\t\n"));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertTrue(service.verifyPin("  1234  "));
        assertTrue(service.verifyPin("\t1234\n"));
    }

    @Test
    void changedStoredPinIsHonouredAndOldPinRejected() {
        fixture.settings.saveValue(SettingsRepository.KEY_ADMIN_PIN, "9876");

        assertTrue(service.verifyPin("9876"));
        assertFalse(service.verifyPin("1234"));
    }

    @Test
    void toStringNeverRevealsStoredPin() {
        fixture.settings.saveValue(SettingsRepository.KEY_ADMIN_PIN, "9876");

        assertFalse(service.toString().contains("9876"));
    }

    @Test
    void nullSettingsRepositoryIsRejected() {
        assertThrows(NullPointerException.class, () -> new AdminAccessService(null));
    }

    // ------------------------------------------------------------------
    // Kiosk mode
    // ------------------------------------------------------------------

    @Test
    void currentModeDefaultsToEntry() {
        assertEquals(KioskMode.ENTRY, service.currentMode());
    }

    @Test
    void absentStoredModeIsEntry() {
        fixture.deleteSetting(SettingsRepository.KEY_KIOSK_MODE);

        assertEquals(KioskMode.ENTRY, service.currentMode());
    }

    @Test
    void exitModeIsPersistedAndReadBackAfterReopen() {
        service.changeMode(KioskMode.EXIT);

        assertEquals(KioskMode.EXIT, service.currentMode());
        assertEquals("EXIT", fixture.settings.findValue(SettingsRepository.KEY_KIOSK_MODE)
                .orElseThrow());

        Database reopened = new Database(tempDir.resolve("test.db"));
        AdminAccessService fresh = new AdminAccessService(new JdbcSettingsRepository(reopened));
        assertEquals(KioskMode.EXIT, fresh.currentMode());

        fresh.changeMode(KioskMode.ENTRY);
        assertEquals(KioskMode.ENTRY, service.currentMode());
    }

    @Test
    void invalidStoredModeFallsBackToEntry() {
        for (String bad : new String[] {"", "   ", "PARKED", "exit", "Exit", "garbage"}) {
            fixture.settings.saveValue(SettingsRepository.KEY_KIOSK_MODE, bad);

            assertEquals(KioskMode.ENTRY, service.currentMode(), "stored value: '" + bad + "'");
        }
    }

    @Test
    void changeModeTouchesOnlyTheKioskModeSetting() {
        fixture.active("S-1", "KA01AB1234", VehicleType.CAR, "G-01",
                AdminTestSupport.NOW.minus(Duration.ofHours(1)));
        fixture.completed("S-2", "KA01AB5678", VehicleType.BIKE, "G-02",
                AdminTestSupport.NOW.minus(Duration.ofHours(3)),
                AdminTestSupport.NOW.minus(Duration.ofHours(2)));
        fixture.settings.saveValue(SettingsRepository.KEY_ADMIN_PIN, "4321");

        long sessionsBefore = fixture.number("SELECT COUNT(*) FROM parking_sessions");
        long activeBefore = fixture.number(
                "SELECT COUNT(*) FROM parking_sessions WHERE exit_time IS NULL");
        long paymentsBefore = fixture.number("SELECT COUNT(*) FROM payments");
        long slotsBefore = fixture.number("SELECT COUNT(*) FROM parking_slots");
        Map<String, String> settingsBefore = fixture.settings.findAll();

        service.changeMode(KioskMode.EXIT);

        assertEquals(sessionsBefore, fixture.number("SELECT COUNT(*) FROM parking_sessions"));
        assertEquals(activeBefore, fixture.number(
                "SELECT COUNT(*) FROM parking_sessions WHERE exit_time IS NULL"));
        assertEquals(paymentsBefore, fixture.number("SELECT COUNT(*) FROM payments"));
        assertEquals(slotsBefore, fixture.number("SELECT COUNT(*) FROM parking_slots"));

        Map<String, String> settingsAfter = fixture.settings.findAll();
        assertEquals(settingsBefore.keySet(), settingsAfter.keySet());
        assertEquals("4321", settingsAfter.get(SettingsRepository.KEY_ADMIN_PIN));
        assertEquals("EXIT", settingsAfter.get(SettingsRepository.KEY_KIOSK_MODE));
    }

    @Test
    void nullModeIsRejectedWithoutWriting() {
        assertThrows(NullPointerException.class, () -> service.changeMode(null));
        assertEquals(KioskMode.ENTRY, service.currentMode());
    }
}
