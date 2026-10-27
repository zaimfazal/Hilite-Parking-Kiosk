package com.hilite.parking.repository;

/**
 * Unchecked wrapper for a JDBC or SQLite failure.
 *
 * <p>Repositories catch {@link java.sql.SQLException}, keep it as the cause, and throw this
 * exception with a short message describing the operation that failed. The cause is never
 * swallowed, so the original SQLite diagnostic is always available for debugging.</p>
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
