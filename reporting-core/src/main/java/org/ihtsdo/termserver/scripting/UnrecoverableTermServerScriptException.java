package org.ihtsdo.termserver.scripting;

import org.ihtsdo.otf.exception.TermServerScriptException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnrecoverableTermServerScriptException extends TermServerScriptException {

	private static final Logger LOGGER = LoggerFactory.getLogger(UnrecoverableTermServerScriptException.class);

	private static final long serialVersionUID = 1L;

	public UnrecoverableTermServerScriptException(String msg) {
		super(msg);
	}

}
