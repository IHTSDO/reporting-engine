package org.ihtsdo.termserver.scripting.reports.gmdn.utils;

/**
 * The GmdnException class represents an exception that can occur in the GMDN system.
 * It extends the Java Exception class and provides constructors to create an exception with a message and, optionally, a cause.
 * <p>
 * Example usage:
 * <p>
 * <code>
 * GmdnException exception = new GmdnException("An error occurred");
 * </code>
 */
public class GmdnException extends Exception {
    public GmdnException(String message, Throwable cause) {
        super(message, cause);
    }

    public GmdnException(String message, Object... args) {
        super(String.format(message, args));
    }
}
