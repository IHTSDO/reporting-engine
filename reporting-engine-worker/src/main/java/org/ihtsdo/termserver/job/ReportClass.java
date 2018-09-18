package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportManager;

public interface ReportClass extends JobClass {
	
	public String getReportName();

	public void setExclusions(String[] exclusions) throws TermServerScriptException;

	public void setReportManager(ReportManager reportManager);

}
