package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

public interface JobClass {

	public void postInit(String subHierarchyStr) throws TermServerScriptException;
}
