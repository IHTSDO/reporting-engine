package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;

import java.io.File;

public interface DataLoader {

	void download(File archive) throws TermServerScriptException;

}
