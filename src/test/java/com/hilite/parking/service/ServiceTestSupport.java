package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared setup and raw-SQL helpers for the Phase 4 allocation-service tests.
 *
 * <p>Every test owns a fresh {@code @TempDir}; this helper only builds the database inside that
 * directory and injects one fixed clock. Occupancy is always created through the session
 * repository, exactly as the application would create it, so a test never has to guess how a
 * bay is marked as taken. The raw statements exist only for the few tests that must prove
 * storage or force a database failure.</p>
 */
final class ServiceTestSupport {

    /** Fixed entry instant used by the injected clock and by every seeded session. */
    static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T10:15:30Z");

    /** Clock injected into every service under test. */
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    /** The frozen 24-bay layout in allocation order: Ground first, then ascending bay. */
    static final List<String> ALL_SLOT_IDS = layoutOrder();

    /** The four charging-capable bays in allocation order. */
    static final List<String> CHARGING_SLOT_IDS = List.of("G-03", "G-08", "F1-05", "F1-12");

    /** The twenty bays without a charger, in allocation order. */
    static final List<String> REGULAR_SLOT_IDS = ALL_SLOT_IDS.stream()
            .filter(slotId -> !CHARGING_SLOT_IDS.contains(slotId))
            .toList();

    private ServiceTestSupport() {
    }

    static Path databaseFile(Path tempDir) {
        return tempDir.resolve("test.db");
    }

    static Database initializedDatabase(Path tempDir) {
        Database database = new Database(databaseFile(tempDir));
        new DatabaseInitializer(database).initialize();
        return database;
    }

    static ParkingService newService(ParkingSlotRepository slots,
                                     ParkingSessionRepository sessions) {
        return new ParkingService(slots, sessions, FIXED_CLOCK);
    }

    /** Occupies one bay with a fresh active session, using a fixed earlier entry time. */
    static void occupy(Database database, String slotId, String registration) {
        occupy(database, slotId, registration, FIXED_INSTANT.minus(Duration.ofMinutes(30)));
    }

    /** Occupies one bay with a fresh active session at the supplied entry time. */
    static void occupy(Database database, String slotId, String registration,
                       Instant entryTime) {
        new JdbcParkingSessionRepository(database).insertActive(new ParkingSession(
                "SEED-" + slotId, registration, VehicleType.CAR, false, slotId,
                entryTime, null, null));
    }

    /**
     * Occupies every listed bay. Registrations are derived from the slot id, so the helper can
     * be called more than once in one test without colliding on the active-registration index.
     */
    static void occupySlots(Database database, List<String> slotIds) {
        int index = 0;
        for (String slotId : slotIds) {
            index++;
            occupy(database, slotId, "SEED" + slotId.replace("-", ""),
                    FIXED_INSTANT.minusSeconds(60L * index));
        }
    }

    /** Executes one raw statement, used to install a test-only trigger. */
    static void execute(Database database, String sql) {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new AssertionError("raw statement failed: " + sql, e);
        }
    }

    static String scalarString(Database database, String sql, Object... parameters) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("raw query returned no row: " + sql);
                }
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("raw query failed: " + sql, e);
        }
    }

    private static List<String> layoutOrder() {
        List<String> slotIds = new ArrayList<>(24);
        for (String floorCode : List.of("G", "F1")) {
            for (int bay = 1; bay <= 12; bay++) {
                slotIds.add(floorCode + "-" + (bay < 10 ? "0" : "") + bay);
            }
        }
        return List.copyOf(slotIds);
    }

    private static void bind(PreparedStatement statement, Object... parameters)
            throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
