package com.hilite.parking.preview;

import java.time.Duration;
import java.time.LocalDateTime;

/** PHASE 1 PREVIEW ONLY. A sample long-stay row for the dashboard alert list. */
public record PreviewLongStay(
        String registration,
        String bay,
        String floorName,
        LocalDateTime entryTime,
        Duration elapsed
) {
}
