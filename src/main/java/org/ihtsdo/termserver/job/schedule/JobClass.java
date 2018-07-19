package org.ihtsdo.termserver.job.schedule;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

public interface JobClass {

	public void postInit(String subHierarchyStr) throws TermServerScriptException;
}
