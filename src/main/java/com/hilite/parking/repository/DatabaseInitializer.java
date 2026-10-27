package com.hilite.parking.repository;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.Zone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Creates the SQLite schema, seeds the fixed 24-bay layout, and installs demo settings.
 *
 * <p><strong>One transaction.</strong> All schema, seed, and validation work runs on a single
 * connection with auto-commit disabled. SQLite makes DDL transactional, so a failure anywhere
 * (including a failed validation) rolls the whole unit of work back: either the database is
 * fully initialized, or it is left exactly as it was. A rollback failure is attached to the
 * original exception as a suppressed exception so the primary diagnostic is not lost.</p>
 *
 * <p><strong>Idempotent and non-destructive.</strong> Running {@link #initialize()} again is
 * safe. Tables and indexes use {@code IF NOT EXISTS}; the layout is inserted only when
 * {@code parking_slots} is empty; settings use {@code INSERT OR IGNORE} so an edited PIN or
 * kiosk mode survives a restart. Sessions and payments are never deleted. If rows already
 * exist, they are read and compared with the frozen expected layout instead of being repaired
 * or replaced; any mismatch is reported and the transaction is rolled back.</p>
 *
 * <p>This class performs no UI work and no fee logic. It only owns database creation and the
 * one-time seed.</p>
 */
public final class DatabaseInitializer {

    private static final int BAYS_PER_FLOOR = 12;
    private static final int LAST_ZONE_A_BAY = 6;

    private static final String DEFAULT_ADMIN_PIN = "1234";
    private static final String DEFAULT_KIOSK_MODE = "ENTRY";

    private static final String SLOT_COUNT_SQL = "SELECT COUNT(*) FROM parking_slots";

    private static final String INSERT_SLOT_SQL = """
            INSERT INTO parking_slots (slot_id, floor, bay_number, zone, charging)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SLOTS_SQL = """
            SELECT slot_id, floor, bay_number, zone, charging
            FROM parking_slots
            ORDER BY CASE floor WHEN 'GROUND' THEN 0 WHEN 'FIRST' THEN 1 ELSE 2 END,
                     bay_number,
                     slot_id
            """;

    private static final String INSERT_SETTING_SQL = """
            INSERT OR IGNORE INTO admin_settings (setting_key, setting_value)
            VALUES (?, ?)
            """;

    private static final List<String> SCHEMA_STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS parking_slots (
                slot_id     TEXT PRIMARY KEY,
                floor       TEXT NOT NULL CHECK (floor IN ('GROUND', 'FIRST')),
                bay_number  INTEGER NOT NULL CHECK (bay_number BETWEEN 1 AND 12),
                zone        TEXT NOT NULL CHECK (zone IN ('A', 'B')),
                charging    INTEGER NOT NULL CHECK (charging IN (0, 1)),
                CHECK (zone = CASE WHEN bay_number <= 6 THEN 'A' ELSE 'B' END),
                UNIQUE (floor, bay_number)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS parking_sessions (
                session_id       TEXT PRIMARY KEY,
                registration     TEXT NOT NULL CHECK (length(trim(registration)) > 0),
                vehicle_type     TEXT NOT NULL CHECK (vehicle_type IN ('CAR', 'BIKE')),
                charging_requested INTEGER NOT NULL CHECK (charging_requested IN (0, 1)),
                slot_id          TEXT NOT NULL REFERENCES parking_slots(slot_id),
                entry_time       TEXT NOT NULL CHECK (length(entry_time) = 30),
                exit_time        TEXT CHECK (exit_time IS NULL OR length(exit_time) = 30),
                final_fee_paise  INTEGER CHECK (final_fee_paise IS NULL OR final_fee_paise >= 0),
                CHECK ((exit_time IS NULL) = (final_fee_paise IS NULL)),
                CHECK (exit_time IS NULL OR exit_time >= entry_time)
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sessions_active_registration
                ON parking_sessions(registration) WHERE exit_time IS NULL
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sessions_active_slot
                ON parking_sessions(slot_id) WHERE exit_time IS NULL
            """,
            """
            CREATE INDEX IF NOT EXISTS ix_sessions_entry_time
                ON parking_sessions(entry_time)
            """,
            """
            CREATE TABLE IF NOT EXISTS payments (
                payment_id    TEXT PRIMARY KEY,
                session_id    TEXT NOT NULL REFERENCES parking_sessions(session_id),
                amount_paise  INTEGER NOT NULL CHECK (amount_paise >= 0),
                status        TEXT NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED')),
                confirmed_at  TEXT CHECK (confirmed_at IS NULL OR length(confirmed_at) = 30),
                CHECK ((status = 'CONFIRMED' AND confirmed_at IS NOT NULL)
                    OR (status = 'PENDING' AND confirmed_at IS NULL))
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS ix_payments_session_id ON payments(session_id)
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_confirmed_session
                ON payments(session_id) WHERE status = 'CONFIRMED'
            """,
            """
            CREATE TABLE IF NOT EXISTS admin_settings (
                setting_key   TEXT PRIMARY KEY CHECK (length(trim(setting_key)) > 0),
                setting_value TEXT NOT NULL
            )
            """
    );

    private static final List<TableSpec> REQUIRED_SCHEMA = List.of(
            new TableSpec("parking_slots",
                    List.of("slot_id", "floor", "bay_number", "zone", "charging")),
            new TableSpec("parking_sessions",
                    List.of("session_id", "registration", "vehicle_type", "charging_requested",
                            "slot_id", "entry_time", "exit_time", "final_fee_paise")),
            new TableSpec("payments",
                    List.of("payment_id", "session_id", "amount_paise", "status",
                            "confirmed_at")),
            new TableSpec("admin_settings",
                    List.of("setting_key", "setting_value"))
    );

    private final Database database;

    /**
     * Creates an initializer for one database handle.
     *
     * @throws NullPointerException if {@code database} is {@code null}
     */
    public DatabaseInitializer(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    /**
     * Creates the schema and seeds the layout/settings, or validates an existing database.
     *
     * <p>The parent directory of the database file is created first when necessary. Then one
     * transaction performs schema creation, schema verification, layout seeding or validation,
     * and settings seeding. Nothing is committed unless every step succeeds.</p>
     *
     * @throws DataAccessException if the directory cannot be created, SQL fails, the existing
     *                             schema is incompatible, or the existing layout is invalid
     */
    public void initialize() {
        createDatabaseDirectory();

        Path file = database.file().toAbsolutePath();
        try (Connection connection = database.openConnection()) {
            String step = "starting the initialization transaction";
            try {
                connection.setAutoCommit(false);

                step = "creating the schema";
                createSchema(connection);

                step = "verifying the schema";
                verifySchema(connection, file);

                step = "checking or seeding the parking layout";
                seedLayout(connection);

                step = "seeding the default settings";
                seedSettings(connection);

                step = "committing the initialization transaction";
                connection.commit();
            } catch (SQLException e) {
                rollback(connection, e);
                throw new DataAccessException(
                        "Database initialization failed while " + step + " (" + file + "): "
                                + e.getMessage(),
                        e);
            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Database initialization finished but the SQLite connection could not be closed ("
                            + file + "): " + e.getMessage(),
                    e);
        }
    }

    /** Creates {@code data/} (or whatever parent the configured file needs) when missing. */
    private void createDatabaseDirectory() {
        Path parent = database.file().toAbsolutePath().getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new DataAccessException(
                    "Could not create the database directory " + parent + ": " + e.getMessage(), e);
        }
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : SCHEMA_STATEMENTS) {
                statement.execute(sql);
            }
        }
    }

    /**
     * Confirms that every required table and column is present. This catches a pre-existing
     * file whose table was created differently: {@code CREATE TABLE IF NOT EXISTS} would
     * otherwise leave it untouched and later repositories would fail with confusing SQL
     * errors.
     */
    private static void verifySchema(Connection connection, Path file) throws SQLException {
        for (TableSpec spec : REQUIRED_SCHEMA) {
            List<String> foundColumns = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "PRAGMA table_info(" + spec.table() + ")")) {
                while (resultSet.next()) {
                    foundColumns.add(resultSet.getString("name"));
                }
            }

            if (foundColumns.isEmpty()) {
                throw new DataAccessException(
                        "Existing database is incompatible: required table '" + spec.table()
                                + "' is missing (" + file + "). No changes were kept.");
            }

            List<String> missing = spec.columns().stream()
                    .filter(column -> !foundColumns.contains(column))
                    .toList();
            if (!missing.isEmpty()) {
                throw new DataAccessException(
                        "Existing database is incompatible: table '" + spec.table()
                                + "' is missing required column(s) " + String.join(", ", missing)
                                + " (" + file + "). No changes were kept.");
            }
        }
    }

    /**
     * Seeds the fixed layout only for an empty table. A non-empty table is validated against
     * the same expected layout and never modified.
     */
    private static void seedLayout(Connection connection) throws SQLException {
        if (countSlots(connection) == 0L) {
            insertExpectedLayout(connection);
        } else {
            validateLayout(connection);
        }
    }

    private static long countSlots(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SLOT_COUNT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    /** Reuses one prepared INSERT for all 24 seeded rows. */
    private static void insertExpectedLayout(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SLOT_SQL)) {
            for (ParkingSlot slot : expectedLayout()) {
                statement.setString(1, slot.slotId());
                statement.setString(2, slot.floor().name());
                statement.setInt(3, slot.bayNumber());
                statement.setString(4, slot.zone().name());
                statement.setInt(5, slot.charging() ? 1 : 0);
                statement.executeUpdate();
            }
        }
    }

    /** Compares existing rows as records, including order, with the frozen expected layout. */
    private static void validateLayout(Connection connection) throws SQLException {
        List<ParkingSlot> expected = expectedLayout();
        List<ParkingSlot> actual = readSlots(connection);
        if (actual.size() != expected.size() || !actual.equals(expected)) {
            throw new DataAccessException(layoutProblem(expected, actual));
        }
    }

    private static List<ParkingSlot> readSlots(Connection connection) throws SQLException {
        List<ParkingSlot> slots = new ArrayList<>();
        Set<String> floorAndBay = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(SELECT_SLOTS_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String slotId = resultSet.getString("slot_id");
                String floorText = resultSet.getString("floor");
                int bayNumber = resultSet.getInt("bay_number");
                String zoneText = resultSet.getString("zone");
                int charging = resultSet.getInt("charging");

                if (slotId == null || slotId.isBlank()) {
                    throw invalidLayout("a row has a blank slot_id");
                }
                if (!floorAndBay.add(floorText + "#" + bayNumber)) {
                    throw invalidLayout("duplicate floor/bay " + describeFloorAndBay(
                            floorText, bayNumber) + " (slot " + slotId + ")");
                }
                if (bayNumber < 1) {
                    throw invalidLayout("slot " + slotId + " has bay_number " + bayNumber
                            + " (expected 1..12)");
                }
                if (charging != 0 && charging != 1) {
                    throw invalidLayout("slot " + slotId + " has charging value " + charging
                            + " (expected 0 or 1)");
                }

                slots.add(new ParkingSlot(
                        slotId,
                        parseFloor(floorText, slotId),
                        bayNumber,
                        parseZone(zoneText, slotId),
                        charging == 1));
            }
        }
        return slots;
    }

    private static void seedSettings(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SETTING_SQL)) {
            insertSetting(statement, SettingsRepository.KEY_ADMIN_PIN, DEFAULT_ADMIN_PIN);
            insertSetting(statement, SettingsRepository.KEY_KIOSK_MODE, DEFAULT_KIOSK_MODE);
        }
    }

    private static void insertSetting(PreparedStatement statement, String key, String value)
            throws SQLException {
        statement.setString(1, key);
        statement.setString(2, value);
        statement.executeUpdate();
    }

    /**
     * The one frozen layout: Ground Floor bays 1..12 then First Floor bays 1..12. Bays 1..6 are
     * Zone A and bays 7..12 are Zone B. Only G-03, G-08, F1-05, and F1-12 support charging.
     */
    private static List<ParkingSlot> expectedLayout() {
        List<ParkingSlot> slots = new ArrayList<>(2 * BAYS_PER_FLOOR);
        addFloor(slots, Floor.GROUND);
        addFloor(slots, Floor.FIRST);
        return List.copyOf(slots);
    }

    private static void addFloor(List<ParkingSlot> slots, Floor floor) {
        for (int bay = 1; bay <= BAYS_PER_FLOOR; bay++) {
            String slotId = floor.code() + "-" + (bay < 10 ? "0" : "") + bay;
            Zone zone = bay <= LAST_ZONE_A_BAY ? Zone.A : Zone.B;
            slots.add(new ParkingSlot(slotId, floor, bay, zone, isChargingBay(slotId)));
        }
    }

    private static boolean isChargingBay(String slotId) {
        return switch (slotId) {
            case "G-03", "G-08", "F1-05", "F1-12" -> true;
            default -> false;
        };
    }

    private static Floor parseFloor(String value, String slotId) {
        if ("GROUND".equals(value)) {
            return Floor.GROUND;
        }
        if ("FIRST".equals(value)) {
            return Floor.FIRST;
        }
        throw invalidLayout("slot " + slotId + " has unsupported floor " + quote(value)
                + " (expected GROUND or FIRST)");
    }

    private static Zone parseZone(String value, String slotId) {
        if ("A".equals(value)) {
            return Zone.A;
        }
        if ("B".equals(value)) {
            return Zone.B;
        }
        throw invalidLayout("slot " + slotId + " has unsupported zone " + quote(value)
                + " (expected A or B)");
    }

    private static String layoutProblem(List<ParkingSlot> expected, List<ParkingSlot> actual) {
        String summary = "Existing parking_slots layout is invalid: expected " + expected.size()
                + " slots (G-01..G-12, F1-01..F1-12); found " + actual.size();

        int shared = Math.min(expected.size(), actual.size());
        for (int i = 0; i < shared; i++) {
            ParkingSlot wanted = expected.get(i);
            ParkingSlot found = actual.get(i);
            if (!wanted.equals(found)) {
                return summary + "; first difference at position " + (i + 1)
                        + ": expected " + describe(wanted) + ", found " + describe(found)
                        + ". Existing rows were left untouched.";
            }
        }

        if (actual.size() < expected.size()) {
            return summary + "; first difference at position " + (shared + 1)
                    + ": expected " + describe(expected.get(shared))
                    + ", but no row was found. Existing rows were left untouched.";
        }
        if (actual.size() > expected.size()) {
            return summary + "; first difference at position " + (shared + 1)
                    + ": unexpected extra row " + describe(actual.get(shared))
                    + ". Existing rows were left untouched.";
        }
        return summary + "; rows do not match the expected layout."
                + " Existing rows were left untouched.";
    }

    private static String describe(ParkingSlot slot) {
        return slot.slotId() + " (floor=" + slot.floor() + ", bay=" + slot.bayNumber()
                + ", zone=" + slot.zone() + ", charging=" + slot.charging() + ")";
    }

    private static String describeFloorAndBay(String floor, int bayNumber) {
        return (floor == null ? "NULL" : floor) + " bay " + bayNumber;
    }

    private static String quote(String value) {
        return value == null ? "NULL" : "'" + value + "'";
    }

    private static DataAccessException invalidLayout(String detail) {
        return new DataAccessException(
                "Existing parking_slots layout is invalid: " + detail
                        + ". Existing rows were left untouched.");
    }

    /**
     * Rolls back after a failure. If rollback itself fails, the rollback exception is attached
     * to the original failure as suppressed rather than replacing it.
     */
    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private record TableSpec(String table, List<String> columns) {
    }
}
