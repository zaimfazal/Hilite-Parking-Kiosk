package com.hilite.parking.web.security;

/**
 * Thrown when an admin action is attempted by a browser session that has not entered the PIN.
 * The message is deliberately generic and never mentions the PIN.
 */
public class AdminAccessDeniedException extends RuntimeException {

    public AdminAccessDeniedException() {
        super("Administrator access is required.");
    }
}
