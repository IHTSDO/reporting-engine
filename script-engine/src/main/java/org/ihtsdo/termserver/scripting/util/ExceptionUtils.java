package org.ihtsdo.termserver.scripting.util;

public class ExceptionUtils {
	
	public static String getExceptionCause(String msg, Throwable t) {
		msg += " due to: ";
		String reason = t.getMessage();
		if (reason == null) {
			String clazz = t.getClass().getSimpleName();
			String location = t.getStackTrace()[0].toString();
			reason = clazz + " at " + location;
		}
		msg += reason;
		if (t.getCause() != null) {
			msg = getExceptionCause(msg, t.getCause());
		}
		return msg;
	}
	
}
