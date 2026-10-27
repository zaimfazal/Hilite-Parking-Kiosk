package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Builds the read-only admin dashboard snapshot.
 *
 * <p>Every number is derived from exactly one {@link ParkingSlotRepository#findAll()} call and
 * one {@link ParkingSessionRepository#findAll()} call, so the snapshot is internally consistent
 * even if another kiosk writes in between. Nothing is cached and nothing is written.</p>
 *
 * <p>Entries are all sessions, exits are completed sessions, and a bay is occupied when an
 * active session references it. Hourly buckets use
 * {@code entryTime.atZone(reportingZone).getHour()}, so the zone is an explicit dependency
 * rather than the JVM default. The peak hour is the hour with the most entries, the earliest
 * such hour on a tie, and empty when no session exists.</p>
 */
public final class StatisticsService {

    private final ParkingSlotRepository slotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final ZoneId reportingZone;

    /**
     * Creates the service.
     *
     * @param slotRepository    read access to the fixed bay layout
     * @param sessionRepository read access to parking sessions
     * @param reportingZone     zone used for the hour-of-day buckets
     * @throws NullPointerException if any argument is {@code null}
     */
    public StatisticsService(ParkingSlotRepository slotRepository,
                             ParkingSessionRepository sessionRepository,
                             ZoneId reportingZone) {
        this.slotRepository = Objects.requireNonNull(slotRepository, "slotRepository is required");
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository is required");
        this.reportingZone = Objects.requireNonNull(reportingZone, "reportingZone is required");
    }

    /**
     * Takes one consistent snapshot of the persisted data.
     *
     * @return the dashboard statistics
     * @throws com.hilite.parking.repository.DataAccessException if a read fails
     */
    public DashboardStatistics snapshot() {
        List<ParkingSlot> slots = slotRepository.findAll();
        List<ParkingSession> sessions = sessionRepository.findAll();

        long totalExits = 0;
        long carsEntered = 0;
        long carsExited = 0;
        long bikesEntered = 0;
        long bikesExited = 0;
        long[] hourly = new long[DashboardStatistics.HOURS_PER_DAY];
        Map<String, ParkingSession> activeBySlot = new HashMap<>();

        for (ParkingSession session : sessions) {
            boolean completed = session.exitTime() != null;
            if (completed) {
                totalExits++;
            } else {
                activeBySlot.put(session.slotId(), session);
            }
            if (session.vehicleType() == VehicleType.CAR) {
                carsEntered++;
                if (completed) {
                    carsExited++;
                }
            } else {
                bikesEntered++;
                if (completed) {
                    bikesExited++;
                }
            }
            hourly[session.entryTime().atZone(reportingZone).getHour()]++;
        }

        Map<Floor, int[]> floorCounts = new EnumMap<>(Floor.class);
        for (Floor floor : Floor.values()) {
            floorCounts.put(floor, new int[2]);
        }
        List<BayStatus> bays = new ArrayList<>(slots.size());
        int occupiedBays = 0;
        int chargingBays = 0;
        int chargingOccupied = 0;
        for (ParkingSlot slot : slots) {
            ParkingSession active = activeBySlot.get(slot.slotId());
            boolean occupied = active != null;
            bays.add(new BayStatus(slot, active));
            int[] counts = floorCounts.get(slot.floor());
            counts[0]++;
            if (occupied) {
                counts[1]++;
                occupiedBays++;
            }
            if (slot.charging()) {
                chargingBays++;
                if (occupied) {
                    chargingOccupied++;
                }
            }
        }

        List<FloorOccupancy> floors = new ArrayList<>(Floor.values().length);
        for (Floor floor : Floor.values()) {
            int[] counts = floorCounts.get(floor);
            floors.add(new FloorOccupancy(floor, counts[0], counts[1]));
        }

        List<HourlyEntries> entriesByHour = new ArrayList<>(DashboardStatistics.HOURS_PER_DAY);
        int peak = -1;
        for (int hour = 0; hour < DashboardStatistics.HOURS_PER_DAY; hour++) {
            entriesByHour.add(new HourlyEntries(hour, hourly[hour]));
            // Strictly greater keeps the earliest hour on a tie.
            if (hourly[hour] > 0 && (peak < 0 || hourly[hour] > hourly[peak])) {
                peak = hour;
            }
        }
        OptionalInt peakHour = peak < 0 ? OptionalInt.empty() : OptionalInt.of(peak);

        return new DashboardStatistics(
                sessions.size(), totalExits,
                carsEntered, carsExited,
                bikesEntered, bikesExited,
                slots.size(), occupiedBays, slots.size() - occupiedBays,
                floors,
                chargingBays, chargingOccupied,
                entriesByHour, peakHour,
                bays);
    }
}
