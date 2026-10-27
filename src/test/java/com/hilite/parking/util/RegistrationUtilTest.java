package com.hilite.parking.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the single registration rule used as the persistence business key: normalization
 * removes whitespace and hyphens before uppercasing, and only normalized values matching the
 * fictional Indian plate format are valid.
 */
class RegistrationUtilTest {

    // ------------------------------------------------------------------
    // Required scenario 35: normalization and empty-input rejection
    // ------------------------------------------------------------------

    @Test
    void normalizeRemovesSeparatorsAndUppercases() {
        assertEquals("KL11AB1234", RegistrationUtil.normalize("kl 11-ab 1234"));
        assertEquals("KA01AB1234", RegistrationUtil.normalize("KA01AB1234"));
        assertEquals("KA01AB1234", RegistrationUtil.normalize(" ka-01 ab\t1234 "));
        assertEquals("KA01AB1234", RegistrationUtil.normalize("K A 0 1 A B 1 2 3 4"));
    }

    @Test
    void normalizeRejectsNullEmptyAndSeparatorOnlyValues() {
        assertThrows(NullPointerException.class, () -> RegistrationUtil.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> RegistrationUtil.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> RegistrationUtil.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> RegistrationUtil.normalize(" - "));
        assertThrows(IllegalArgumentException.class, () -> RegistrationUtil.normalize("---"));
    }

    // ------------------------------------------------------------------
    // Required scenario 36: validity of the normalized plate
    // ------------------------------------------------------------------

    @Test
    void isValidAcceptsNormalizedPlates() {
        assertTrue(RegistrationUtil.isValid("KA01AB1234"));
        assertTrue(RegistrationUtil.isValid("KL11A1234"),
                "One to three letters between the district digits and number are allowed");
    }

    @Test
    void isValidRejectsLowercaseSpacedAndIncompleteValues() {
        assertFalse(RegistrationUtil.isValid("ka01ab1234"), "Lowercase is not normalized");
        assertFalse(RegistrationUtil.isValid("KA 01 AB 1234"), "Spaces are not normalized");
        assertFalse(RegistrationUtil.isValid("KA01AB123"), "Incomplete serial must be rejected");
        assertFalse(RegistrationUtil.isValid("KA01AB12345"), "Overlong serial must be rejected");
        assertFalse(RegistrationUtil.isValid(""), "Empty value must be rejected");
        assertFalse(RegistrationUtil.isValid(null), "Null value must be rejected");
    }
}
