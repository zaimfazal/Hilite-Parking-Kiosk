package com.hilite.parking.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Zone-explicit {@link FormatUtil} overloads and the hour-bucket label (audit R8). */
class FormatUtilTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    /** 20:30 UTC is 02:00 the next day in Kolkata (UTC+05:30). */
    private static final Instant EVENING_UTC = Instant.parse("2026-10-26T20:30:00Z");

    @Test
    void dateTimeUsesTheGivenZone() {
        assertEquals("26 Oct 2026, 08:30 PM", FormatUtil.dateTime(EVENING_UTC, ZoneOffset.UTC));
        assertEquals("27 Oct 2026, 02:00 AM", FormatUtil.dateTime(EVENING_UTC, KOLKATA));
    }

    @Test
    void timeUsesTheGivenZone() {
        assertEquals("08:30 PM", FormatUtil.time(EVENING_UTC, ZoneOffset.UTC));
        assertEquals("02:00 AM", FormatUtil.time(EVENING_UTC, KOLKATA));
    }

    @Test
    void nullInstantIsADashAndNullZoneIsRejected() {
        assertEquals("—", FormatUtil.dateTime(null, KOLKATA));
        assertEquals("—", FormatUtil.time(null, KOLKATA));
        assertThrows(NullPointerException.class, () -> FormatUtil.dateTime(EVENING_UTC, null));
        assertThrows(NullPointerException.class, () -> FormatUtil.time(EVENING_UTC, null));
    }

    @Test
    void hourLabelCoversTheWholeDay() {
        assertEquals("12 AM", FormatUtil.hourLabel(0));
        assertEquals("1 AM", FormatUtil.hourLabel(1));
        assertEquals("11 AM", FormatUtil.hourLabel(11));
        assertEquals("12 PM", FormatUtil.hourLabel(12));
        assertEquals("1 PM", FormatUtil.hourLabel(13));
        assertEquals("4 PM", FormatUtil.hourLabel(16));
        assertEquals("11 PM", FormatUtil.hourLabel(23));
    }

    @Test
    void hourLabelRejectsHoursOutsideTheDay() {
        assertThrows(IllegalArgumentException.class, () -> FormatUtil.hourLabel(-1));
        assertThrows(IllegalArgumentException.class, () -> FormatUtil.hourLabel(24));
    }

    @Test
    void existingLocalDateTimeFormattersAreUnchanged() {
        LocalDateTime value = LocalDateTime.of(2026, 10, 26, 16, 5);
        assertEquals("26 Oct 2026, 04:05 PM", FormatUtil.dateTime(value));
        assertEquals("04:05 PM", FormatUtil.time(value));
        assertEquals("26 Oct, 04:05 PM", FormatUtil.shortDateTime(value));
    }
}
