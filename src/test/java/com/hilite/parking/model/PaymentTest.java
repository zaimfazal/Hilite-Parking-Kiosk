package com.hilite.parking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Protects the Phase 2 {@link Payment} record: a payment always carries valid
 * IDs and a non-negative amount, and its status must agree with the presence of
 * a confirmation timestamp (PENDING has none, CONFIRMED has one). Persistence
 * and duplicate-payment rules are out of scope here (Phase 3).
 */
class PaymentTest {

    private static final String PAYMENT_ID = "P-001";
    private static final String SESSION_ID = "S-001";
    private static final BigDecimal AMOUNT = new BigDecimal("5.00");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-10-25T11:00:00Z");

    @Test
    void pendingPaymentWithoutTimestampIsValid() {
        Payment payment = new Payment(
                PAYMENT_ID, SESSION_ID, AMOUNT, PaymentStatus.PENDING, null);

        assertEquals(PAYMENT_ID, payment.paymentId());
        assertEquals(SESSION_ID, payment.sessionId());
        assertEquals(0, AMOUNT.compareTo(payment.amount()));
        assertEquals(PaymentStatus.PENDING, payment.status());
        assertNull(payment.confirmedAt());
    }

    @Test
    void confirmedPaymentWithTimestampIsValid() {
        Payment payment = new Payment(
                PAYMENT_ID, SESSION_ID, AMOUNT, PaymentStatus.CONFIRMED, CONFIRMED_AT);

        assertEquals(PaymentStatus.CONFIRMED, payment.status());
        assertEquals(CONFIRMED_AT, payment.confirmedAt());
        assertEquals(0, AMOUNT.compareTo(payment.amount()));
    }

    @Test
    void nullPaymentIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Payment(null, SESSION_ID, AMOUNT, PaymentStatus.PENDING, null));
    }

    @Test
    void emptyPaymentIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("", SESSION_ID, AMOUNT, PaymentStatus.PENDING, null));
    }

    @Test
    void whitespacePaymentIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("   ", SESSION_ID, AMOUNT, PaymentStatus.PENDING, null));
    }

    @Test
    void nullSessionIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Payment(PAYMENT_ID, null, AMOUNT, PaymentStatus.PENDING, null));
    }

    @Test
    void emptySessionIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(PAYMENT_ID, "", AMOUNT, PaymentStatus.PENDING, null));
    }

    @Test
    void whitespaceSessionIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(PAYMENT_ID, "   ", AMOUNT, PaymentStatus.PENDING, null));
    }

    @Test
    void nullAmountIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Payment(PAYMENT_ID, SESSION_ID, null, PaymentStatus.PENDING, null));
    }

    @Test
    void negativeAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(PAYMENT_ID, SESSION_ID, new BigDecimal("-5.00"),
                        PaymentStatus.PENDING, null));
    }

    @Test
    void nullStatusIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Payment(PAYMENT_ID, SESSION_ID, AMOUNT, null, null));
    }

    @Test
    void confirmedPaymentWithoutTimestampIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(PAYMENT_ID, SESSION_ID, AMOUNT,
                        PaymentStatus.CONFIRMED, null));
    }

    @Test
    void pendingPaymentWithTimestampIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(PAYMENT_ID, SESSION_ID, AMOUNT,
                        PaymentStatus.PENDING, CONFIRMED_AT));
    }
}
