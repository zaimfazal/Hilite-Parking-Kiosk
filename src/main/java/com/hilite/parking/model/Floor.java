package com.hilite.parking.model;

/** The two fictional parking floors. */
public enum Floor {
    GROUND("G", "Ground Floor"),
    FIRST("F1", "First Floor");

    private final String code;
    private final String displayName;

    Floor(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }
}
