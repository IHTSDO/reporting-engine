package org.ihtsdo.termserver.scripting.fixes;

public class TermServerFixException extends Exception {

	private static final long serialVersionUID = 1L;

	public TermServerFixException(String msg, Throwable t) {
		super(msg, t);
	}

	public TermServerFixException(String msg) {
		super(msg);
	}
}
