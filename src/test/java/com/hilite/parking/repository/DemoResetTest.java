package com.hilite.parking.repository;

import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;
import com.hilite.parking.repository.jdbc.JdbcSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 demo reset: {@link DatabaseInitializer#resetDemoData()} clears sessions and payments,
 * restores the frozen layout, keeps the PIN, sets ENTRY mode, keeps the schema and the file, and
 * rolls everything back when any step fails. Every test uses its own {@code @TempDir} file.
 */
class DemoResetTest {

    private static final Instant ENTRY = RepositoryTestSupport.ENTRY_TIME;
    private static final Set<String> CHARGING = Set.of("G-03", "G-08", "F1-05", "F1-12");

    @TempDir
    Path tempDir;

    private Database database;
    private JdbcParkingSessionRepository sessions;
    private JdbcPaymentRepository payments;
    private JdbcSettingsRepository settings;
    private JdbcParkingSlotRepository slots;

    @BeforeEach
    void setUp() {
        database = RepositoryTestSupport.initializedDatabase(tempDir);
        sessions = new JdbcParkingSessionRepository(database);
        payments = new JdbcPaymentRepository(database);
        settings = new JdbcSettingsRepository(database);
        slots = new JdbcParkingSlotRepository(database);
    }

    @Test
    void resetClearsSessionsAndPayments() {
        seedHistory();
        assertEquals(3, sessions.countEntries());
        assertEquals(2, payments.findAll().size());

        reset();

        assertEquals(0, sessions.countEntries());
        assertEquals(0, payments.findAll().size());
        assertEquals(0, RepositoryTestSupport.scalarLong(database,
                "SELECT COUNT(*) FROM parking_sessions"));
        assertEquals(0, RepositoryTestSupport.scalarLong(database,
                "SELECT COUNT(*) FROM payments"));
    }

    @Test
    void afterResetAllTwentyFourBaysAreFreeAndFourAreCharging() {
        seedHistory();

        reset();

        List<ParkingSlot> layout = slots.findAll();
        assertEquals(24, layout.size());
        assertTrue(sessions.findActive().isEmpty(), "No bay may stay occupied after a reset");
        for (ParkingSlot slot : layout) {
            assertTrue(sessions.findActiveBySlot(slot.slotId()).isEmpty(), slot.slotId());
        }
        Set<String> charging = new HashSet<>();
        for (ParkingSlot slot : slots.findCharging()) {
            charging.add(slot.slotId());
        }
        assertEquals(CHARGING, charging);
    }

    @Test
    void resetKeepsAChangedPin() {
        settings.saveValue(SettingsRepository.KEY_ADMIN_PIN, "4321");

        reset();

        assertEquals(Optional.of("4321"), settings.findValue(SettingsRepository.KEY_ADMIN_PIN));
    }

    @Test
    void resetRestoresDefaultPinOnlyWhenItIsMissing() {
        RepositoryTestSupport.executeSql(database,
                "DELETE FROM admin_settings WHERE setting_key = ?",
                SettingsRepository.KEY_ADMIN_PIN);

        reset();

        assertEquals(Optional.of("1234"), settings.findValue(SettingsRepository.KEY_ADMIN_PIN));
    }

    @Test
    void resetSetsKioskModeToEntry() {
        settings.saveValue(SettingsRepository.KEY_KIOSK_MODE, "EXIT");

        reset();

        assertEquals(Optional.of("ENTRY"), settings.findValue(SettingsRepository.KEY_KIOSK_MODE));
    }

    @Test
    void resetRestoresTamperedLayoutToTheExactTwentyFourBays() {
        List<ParkingSlot> original = slots.findAll();
        // Tamper: one bay deleted, one charging flag changed. Startup would refuse this file.
        RepositoryTestSupport.executeSql(database,
                "DELETE FROM parking_slots WHERE slot_id = 'G-05'");
        RepositoryTestSupport.executeSql(database,
                "UPDATE parking_slots SET charging = 1 WHERE slot_id = 'F1-01'");
        assertThrows(DataAccessException.class, () -> new DatabaseInitializer(database).initialize());
        assertEquals(23, slots.count());

        reset();

        assertEquals(original, slots.findAll(), "Reset must restore the exact frozen layout");
        // Startup validation now accepts the file again.
        new DatabaseInitializer(database).initialize();
    }

    @Test
    void resetKeepsSchemaIndexesAndTheSameFile() {
        seedHistory();
        Path file = database.file().toAbsolutePath();

        reset();

        assertTrue(Files.exists(file), "The database file must never be deleted");
        Set<String> names = new HashSet<>(RepositoryTestSupport.stringColumn(database,
                "SELECT name FROM sqlite_master WHERE type IN ('table', 'index')"));
        assertTrue(names.containsAll(Set.of(
                "parking_slots", "parking_sessions", "payments", "admin_settings",
                "ux_sessions_active_registration", "ux_sessions_active_slot",
                "ux_payments_confirmed_session")), "Schema must survive the reset: " + names);

        // The cleared database works normally afterwards.
        sessions.insertActive(RepositoryTestSupport.activeCar("S-NEW", "KA09ZZ0001", "G-01", ENTRY));
        assertEquals(1, sessions.countEntries());
    }

    @Test
    void resetIsRepeatable() {
        reset();
        reset();
        assertEquals(24, slots.count());
        assertEquals(0, sessions.countEntries());
    }

    @Test
    void failureAfterPaymentsWereDeletedRollsBackEverything() {
        seedHistory();
        settings.saveValue(SettingsRepository.KEY_KIOSK_MODE, "EXIT");
        RepositoryTestSupport.executeSql(database,
                "DELETE FROM parking_slots WHERE slot_id = 'F1-11'");
        // The trigger fires only once payments are already gone, i.e. only after the reset's
        // DELETE FROM payments has really run inside the transaction. The failure therefore
        // happens after a genuine partial write, which the rollback must undo.
        RepositoryTestSupport.executeSql(database, """
                CREATE TRIGGER fail_reset_after_payments
                BEFORE DELETE ON parking_sessions
                WHEN (SELECT COUNT(*) FROM payments) = 0
                BEGIN
                    SELECT RAISE(ABORT, 'test-only failure after payments were deleted');
                END
                """);

        DataAccessException failure = assertThrows(DataAccessException.class, this::reset);
        assertTrue(failure.getMessage().contains("deleting parking sessions"), failure.getMessage());

        assertEquals(2, payments.findAll().size(), "Deleted payments must be rolled back");
        assertEquals(3, sessions.countEntries(), "Sessions must be unchanged");
        assertEquals(1, sessions.findActive().size());
        assertEquals(Optional.of("EXIT"), settings.findValue(SettingsRepository.KEY_KIOSK_MODE));
        assertEquals(23, slots.count(), "Layout repair must be rolled back too");
    }

    @Test
    void resetOnIncompatibleSchemaFailsWithoutChanges() {
        seedHistory();
        RepositoryTestSupport.executeSql(database, "DROP TABLE admin_settings");

        DataAccessException failure = assertThrows(DataAccessException.class, this::reset);
        assertTrue(failure.getMessage().contains("admin_settings"), failure.getMessage());
        assertEquals(3, sessions.countEntries());
        assertEquals(2, payments.findAll().size());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void reset() {
        new DatabaseInitializer(database).resetDemoData();
    }

    /** One active session and two completed, paid sessions. */
    private void seedHistory() {
        JdbcExitCompletionRepository exits = new JdbcExitCompletionRepository(database);
        sessions.insertActive(RepositoryTestSupport.activeCar("S-1", "KA01AB0001", "G-01", ENTRY));
        sessions.insertActive(RepositoryTestSupport.activeCar("S-2", "KA01AB0002", "G-02", ENTRY));
        sessions.insertActive(RepositoryTestSupport.activeCar("S-3", "KA01AB0003", "G-03", ENTRY));

        Instant exit = ENTRY.plus(Duration.ofHours(3));
        assertTrue(exits.complete("S-2", exit, new BigDecimal("5.00"),
                RepositoryTestSupport.confirmedPayment("P-2", "S-2", "5.00", exit)));
        assertTrue(exits.complete("S-3", exit, new BigDecimal("5.00"),
                RepositoryTestSupport.confirmedPayment("P-3", "S-3", "5.00", exit)));
    }
}
