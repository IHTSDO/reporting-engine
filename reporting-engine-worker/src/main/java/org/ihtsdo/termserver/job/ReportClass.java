package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportManager;

public interface ReportClass extends JobClass {
	
	//Returns the Google URL of the report
	public String runReport() throws TermServerScriptException;
	
	public String getReportName();

	public void setExclusions(String[] exclusions) throws TermServerScriptException;

	public void setReportManager(ReportManager reportManager);

}
