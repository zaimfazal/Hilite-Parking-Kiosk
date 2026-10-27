package com.hilite.parking.deploy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;

/**
 * Replaces the application {@link Clock} with a {@link MutableClock}, for use with
 * {@code @Import(MutableClockConfig.class)}.
 *
 * <p>Deliberately <em>not</em> annotated with {@code @Configuration}/{@code @TestConfiguration}:
 * the class is only ever imported explicitly, so component scanning in another test's context
 * can never pick up a second {@code @Primary Clock}.</p>
 */
public class MutableClockConfig {

    /** Start instant used by every test that imports this configuration. */
    public static final Instant START = Instant.parse("2026-10-28T08:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }
}
