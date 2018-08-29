package org.ihtsdo.termserver.scripting;

public class TermServerScriptException extends Exception {

	private static final long serialVersionUID = 1L;

	public TermServerScriptException(String msg, Throwable t) {
		super(msg, t);
	}

	public TermServerScriptException(String msg) {
		super(msg);
	}
}
