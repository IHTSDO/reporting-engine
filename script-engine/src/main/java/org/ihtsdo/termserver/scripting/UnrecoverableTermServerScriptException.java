package org.ihtsdo.termserver.scripting;

import org.ihtsdo.otf.exception.TermServerScriptException;

public class UnrecoverableTermServerScriptException extends TermServerScriptException {

	private static final long serialVersionUID = 1L;

	public UnrecoverableTermServerScriptException(String msg) {
		super(msg);
	}

}
