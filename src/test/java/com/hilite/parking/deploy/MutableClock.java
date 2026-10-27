package com.hilite.parking.deploy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/** A UTC clock that tests move forward by hand. Thread-safe enough for test use. */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = Objects.requireNonNull(start, "start is required");
    }

    public void set(Instant instant) {
        this.now = Objects.requireNonNull(instant, "instant is required");
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("tests use UTC only");
    }
}
