package com.hilite.parking.repository.jdbc;

import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.SettingsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link SettingsRepository}.
 *
 * <p>A plain key/value store: the PIN and kiosk mode are opaque text here. Values are
 * upserted with SQLite's {@code ON CONFLICT ... DO UPDATE}, so saving the same key twice
 * simply replaces the value.</p>
 */
public final class JdbcSettingsRepository implements SettingsRepository {

    private final Database database;

    /** Creates the repository. */
    public JdbcSettingsRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    @Override
    public Optional<String> findValue(String key) {
        Objects.requireNonNull(key, "key is required");
        String sql = "SELECT setting_value FROM admin_settings WHERE setting_key = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.ofNullable(results.getString("setting_value"))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Loading setting", e);
        }
    }

    @Override
    public Map<String, String> findAll() {
        String sql = "SELECT setting_key, setting_value FROM admin_settings ORDER BY setting_key";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            Map<String, String> settings = new LinkedHashMap<>();
            while (results.next()) {
                settings.put(results.getString("setting_key"),
                        results.getString("setting_value"));
            }
            return Collections.unmodifiableMap(settings);
        } catch (SQLException e) {
            throw SqlCodec.failure("Loading settings", e);
        }
    }

    @Override
    public void saveValue(String key, String value) {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(value, "value is required");
        String sql = "INSERT INTO admin_settings (setting_key, setting_value) VALUES (?, ?) "
                + "ON CONFLICT(setting_key) DO UPDATE SET "
                + "setting_value = excluded.setting_value";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw SqlCodec.failure("Saving setting", e);
        }
    }
}
