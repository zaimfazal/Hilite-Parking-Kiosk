package com.hilite.parking.web.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Display formatting for stored registrations. The services store plates without spaces
 * ({@code KL11AB1234}); the kiosk shows them grouped like a real plate ({@code KL 11 AB 1234}).
 * Anything that does not match the demo plate pattern is shown unchanged.
 */
public final class Plates {

    private static final Pattern GROUPS = Pattern.compile("([A-Z]{2})([0-9]{2})([A-Z]{1,3})([0-9]{4})");

    private Plates() {
    }

    public static String display(String registration) {
        if (registration == null) {
            return "";
        }
        Matcher matcher = GROUPS.matcher(registration);
        if (!matcher.matches()) {
            return registration;
        }
        return matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3) + " " + matcher.group(4);
    }
}
