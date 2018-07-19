package org.ihtsdo.termserver.job.schedule;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

public interface ReportClass extends JobClass {
	
	public void runReport() throws TermServerScriptException;
	
	public String getReportName();

}
