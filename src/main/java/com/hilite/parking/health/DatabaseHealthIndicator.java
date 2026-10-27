package com.hilite.parking.health;

import com.hilite.parking.repository.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * Reports whether the configured SQLite file can serve requests.
 *
 * <p>Opens a fresh connection through the shared {@link Database} bean (so the same pragmas and
 * file are used as by the repositories), runs {@code SELECT 1} and confirms that the four
 * application tables exist. Contributes to {@code GET /actuator/health} as the
 * {@code database} component (bean name {@code databaseHealthIndicator}).</p>
 *
 * <p>The health response never carries internal details: a failure is reported as
 * {@code DOWN} with one generic detail, and the real cause (which may contain the file path or
 * SQL) is only written to the server log. Details are additionally hidden by
 * {@code management.endpoint.health.show-details=never}.</p>
 *
 * <p>This check only reads. It never creates, repairs or seeds anything; that is the job of
 * {@link com.hilite.parking.repository.DatabaseInitializer} at startup.</p>
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    /** The only detail ever exposed for a failed check. */
    static final String GENERIC_FAILURE = "Database unavailable";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private static final List<String> REQUIRED_TABLES =
            List.of("parking_slots", "parking_sessions", "payments", "admin_settings");

    private static final String TABLE_EXISTS_SQL =
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";

    private final Database database;

    public DatabaseHealthIndicator(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    @Override
    public Health health() {
        try (Connection connection = database.openConnection()) {
            if (!selectOne(connection)) {
                return down("SELECT 1 returned no row", null);
            }
            for (String table : REQUIRED_TABLES) {
                if (!tableExists(connection, table)) {
                    return down("required table is missing: " + table, null);
                }
            }
            return Health.up().build();
        } catch (Exception e) {
            return down("database check failed", e);
        }
    }

    private static boolean selectOne(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next() && resultSet.getInt(1) == 1;
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(TABLE_EXISTS_SQL)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static Health down(String logReason, Exception cause) {
        // The cause stays in the server log only; the response gets the generic detail.
        LOG.warn("Database health check failed: {}", logReason, cause);
        return Health.down().withDetail("error", GENERIC_FAILURE).build();
    }
}
