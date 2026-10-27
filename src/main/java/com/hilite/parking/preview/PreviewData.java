package com.hilite.parking.preview;

import com.hilite.parking.util.FormatUtil;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PHASE 1 PREVIEW ONLY.
 *
 * <p>This is the single source of sample data for the visual prototype. It is not a repository,
 * does not persist anything, and must not be mistaken for live statistics. Phases 2-6 replace it
 * with the real model, services and SQLite repositories.</p>
 */
public final class PreviewData {

    /** Configurable preview PIN. Demonstration navigation only, never shown in the UI. */
    public static final String ADMIN_PIN = "1234";
    public static final String EXAMPLE_PLATE = "KL 11 AB 1234";

    private static final LocalDateTime SAMPLE_NOW = LocalDateTime.now();

    private static final List<PreviewBay> BAYS = List.of(
            free("G-01", "Ground Floor", "G", 1),
            occupied("G-02", "Ground Floor", "G", 2, false, "KL 11 AB 1234", VehicleType.CAR, 125),
            occupied("G-03", "Ground Floor", "G", 3, true, "KL 09 EV 2201", VehicleType.CAR, 110),
            occupied("G-04", "Ground Floor", "G", 4, false, "KL 07 CD 5678", VehicleType.CAR, 75),
            occupied("G-05", "Ground Floor", "G", 5, false, "KL 11 XY 4321", VehicleType.BIKE, 40),
            free("G-06", "Ground Floor", "G", 6),
            occupied("G-07", "Ground Floor", "G", 7, false, "KL 13 EF 7890", VehicleType.CAR, 872),
            free("G-08", "Ground Floor", "G", 8),
            free("G-09", "Ground Floor", "G", 9),
            occupied("G-10", "Ground Floor", "G", 10, false, "KL 11 GH 2468", VehicleType.BIKE, 200),
            free("G-11", "Ground Floor", "G", 11),
            free("G-12", "Ground Floor", "G", 12),
            free("F1-01", "First Floor", "F1", 1),
            occupied("F1-02", "First Floor", "F1", 2, false, "KL 11 PQ 1122", VehicleType.BIKE, 25),
            free("F1-03", "First Floor", "F1", 3),
            occupied("F1-04", "First Floor", "F1", 4, false, "KL 08 MN 9071", VehicleType.CAR, 305),
            free("F1-05", "First Floor", "F1", 5),
            free("F1-06", "First Floor", "F1", 6),
            free("F1-07", "First Floor", "F1", 7),
            free("F1-08", "First Floor", "F1", 8),
            free("F1-09", "First Floor", "F1", 9),
            free("F1-10", "First Floor", "F1", 10),
            occupied("F1-11", "First Floor", "F1", 11, false, "KL 07 TT 6655", VehicleType.CAR, 903),
            free("F1-12", "First Floor", "F1", 12)
    );

    private PreviewData() {
    }

    /**
     * Preview-only 4 h 24 min car stay, matching the supplied exit design reference.
     */
    public static PreviewExitQuote createExitQuote(String registration) {
        LocalDateTime exitTime = LocalDateTime.now();
        LocalDateTime entryTime = exitTime.minusHours(4).minusMinutes(24);
        return new PreviewExitQuote(
                FormatUtil.normalizePlate(registration),
                VehicleType.CAR,
                entryTime,
                exitTime,
                Duration.ofMinutes(264),
                Duration.ofMinutes(120),
                Duration.ofMinutes(144),
                "₹5 per hour",
                new BigDecimal("15")
        );
    }

    public static List<PreviewBay> bays() {
        return BAYS;
    }

    public static List<PreviewBay> baysForFloor(String floorCode) {
        return BAYS.stream().filter(bay -> bay.floorCode().equals(floorCode)).toList();
    }

    public static PreviewStats stats() {
        int occupied = (int) BAYS.stream().filter(PreviewBay::occupied).count();
        int available = BAYS.size() - occupied;
        int evInUse = (int) BAYS.stream().filter(bay -> bay.occupied() && bay.charging()).count();
        int evTotal = (int) BAYS.stream().filter(PreviewBay::charging).count();
        return new PreviewStats(occupied, available, 38, 29, 24, 14, 18, 11,
                evInUse, evTotal, "4 PM", 6);
    }

    /** Sample long-stay rows derived from the preview bays using the 12-hour rule. */
    public static List<PreviewLongStay> longStayAlerts() {
        LocalDateTime now = LocalDateTime.now();
        return BAYS.stream()
                .filter(PreviewBay::occupied)
                .filter(bay -> bay.entryTime() != null)
                .filter(bay -> Duration.between(bay.entryTime(), now).toMinutes() > 12 * 60)
                .map(bay -> new PreviewLongStay(bay.registration(), bay.id(), bay.floorName(),
                        bay.entryTime(), Duration.between(bay.entryTime(), now)))
                .toList();
    }

    private static PreviewBay free(String id, String floorName, String floorCode, int number) {
        return new PreviewBay(id, floorName, floorCode, zoneFor(number), isCharging(id),
                false, null, null, null);
    }

    private static PreviewBay occupied(String id, String floorName, String floorCode, int number,
                                       boolean charging, String plate, VehicleType type, int minutesAgo) {
        return new PreviewBay(id, floorName, floorCode, zoneFor(number), charging,
                true, plate, type, SAMPLE_NOW.minusMinutes(minutesAgo));
    }

    private static String zoneFor(int bayNumber) {
        return bayNumber <= 6 ? "A" : "B";
    }

    private static boolean isCharging(String id) {
        return id.equals("G-03") || id.equals("G-08") || id.equals("F1-05") || id.equals("F1-12");
    }
}
