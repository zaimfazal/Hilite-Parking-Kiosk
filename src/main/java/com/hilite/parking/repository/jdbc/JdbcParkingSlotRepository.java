package com.hilite.parking.repository.jdbc;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.Zone;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSlotRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only JDBC implementation of {@link ParkingSlotRepository}.
 *
 * <p>One short-lived connection is opened per call. The fixed bay layout is seeded once and
 * never edited by the application, so this class exposes no write method.</p>
 */
public final class JdbcParkingSlotRepository implements ParkingSlotRepository {

    private static final String SELECT_COLUMNS =
            "SELECT slot_id, floor, bay_number, zone, charging FROM parking_slots";

    private static final String ORDER_BY =
            " ORDER BY CASE floor WHEN 'GROUND' THEN 0 WHEN 'FIRST' THEN 1 ELSE 2 END, bay_number";

    private final Database database;

    /** Creates the repository. */
    public JdbcParkingSlotRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    @Override
    public Optional<ParkingSlot> findById(String slotId) {
        Objects.requireNonNull(slotId, "slotId is required");
        String sql = SELECT_COLUMNS + " WHERE slot_id = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slotId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapRow(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Loading parking slot", e);
        }
    }

    @Override
    public List<ParkingSlot> findAll() {
        return querySlots(SELECT_COLUMNS + ORDER_BY, "Loading parking slots");
    }

    @Override
    public List<ParkingSlot> findCharging() {
        return querySlots(SELECT_COLUMNS + " WHERE charging = 1" + ORDER_BY,
                "Loading charging parking slots");
    }

    @Override
    public long count() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(*) FROM parking_slots");
             ResultSet results = statement.executeQuery()) {
            return results.next() ? results.getLong(1) : 0L;
        } catch (SQLException e) {
            throw SqlCodec.failure("Counting parking slots", e);
        }
    }

    private List<ParkingSlot> querySlots(String sql, String action) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<ParkingSlot> slots = new ArrayList<>();
            while (results.next()) {
                slots.add(mapRow(results));
            }
            return List.copyOf(slots);
        } catch (SQLException e) {
            throw SqlCodec.failure(action, e);
        }
    }

    private static ParkingSlot mapRow(ResultSet results) throws SQLException {
        String slotId = results.getString("slot_id");
        String floor = results.getString("floor");
        int bayNumber = results.getInt("bay_number");
        String zone = results.getString("zone");
        int charging = results.getInt("charging");
        try {
            return new ParkingSlot(
                    slotId, Floor.valueOf(floor), bayNumber, Zone.valueOf(zone), charging == 1);
        } catch (IllegalArgumentException | NullPointerException corrupt) {
            throw SqlCodec.corruptRow("parking_slots", slotId, corrupt);
        }
    }
}
