package com.hilite.parking.repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Knows where the SQLite database lives and opens correctly configured JDBC connections.
 *
 * <p>The database is a single file, {@code data/hilite-parking.db} by default. SQLite settings
 * such as {@code PRAGMA foreign_keys} and {@code PRAGMA busy_timeout} are per connection, not
 * per file, so every connection returned by {@link #openConnection()} is configured before it
 * is handed out. Connections are never cached or shared: each call creates a fresh connection,
 * which keeps transaction ownership obvious and avoids cross-thread connection reuse.</p>
 *
 * <p>Foreign-key enforcement is verified, not merely requested. If the pragma cannot be applied
 * or does not report {@code 1}, the half-configured connection is closed and the caller gets a
 * {@link DataAccessException} instead of a connection that silently ignores foreign keys.</p>
 */
public final class Database {

    /** Default runtime database file, relative to the application working directory. */
    public static final Path DEFAULT_FILE = Path.of("data", "hilite-parking.db");

    private static final String FOREIGN_KEYS_ON = "PRAGMA foreign_keys = ON";
    private static final String BUSY_TIMEOUT = "PRAGMA busy_timeout = 5000";
    private static final String FOREIGN_KEYS_STATE = "PRAGMA foreign_keys";

    private final Path databaseFile;
    private final String jdbcUrl;

    /**
     * Creates a handle for one SQLite file.
     *
     * @param databaseFile file to use; stored exactly as supplied so {@link #file()} returns it
     *                     unchanged
     * @throws NullPointerException if {@code databaseFile} is {@code null}
     */
    public Database(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile is required");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }

    /** Returns a handle for the default application database file. */
    public static Database defaultDatabase() {
        return new Database(DEFAULT_FILE);
    }

    /** Returns the database file exactly as it was supplied to the constructor. */
    public Path file() {
        return databaseFile;
    }

    /**
     * Opens a new SQLite connection with foreign keys on and a five-second busy timeout.
     *
     * <p>The caller owns the returned connection and must close it. If opening or configuring
     * the connection fails, the connection is closed (when one was created) and a
     * {@link DataAccessException} is thrown with the database location in the message.</p>
     */
    public Connection openConnection() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            applyPragmas(connection);
            return connection;
        } catch (SQLException e) {
            closeAfterFailure(connection, e);
            throw new DataAccessException(
                    "Could not open SQLite database at " + databaseFile.toAbsolutePath()
                            + ": " + e.getMessage(),
                    e);
        }
    }

    private static void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(FOREIGN_KEYS_ON);
            statement.execute(BUSY_TIMEOUT);

            // A freshly opened connection is not in a transaction, so foreign_keys can be set.
            // Verify it actually took effect rather than trusting a silent no-op.
            try (ResultSet resultSet = statement.executeQuery(FOREIGN_KEYS_STATE)) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("SQLite refused to enable foreign key enforcement");
                }
            }
        }
    }

    private static void closeAfterFailure(Connection connection, SQLException failure) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
