package org.ihtsdo.termserver.scripting;

import org.ihtsdo.otf.exception.TermServerScriptException;

import java.io.Serial;

public class UnrecoverableTermServerScriptException extends TermServerScriptException {

	@Serial
	private static final long serialVersionUID = 1L;

	public UnrecoverableTermServerScriptException(String msg) {
		super(msg);
	}

}
