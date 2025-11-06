package org.ihtsdo.termserver.scripting;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.script.dao.ReportManager;
import org.springframework.context.ApplicationContext;

import java.util.List;

public interface JobClass {
	
	public void postInit() throws TermServerScriptException;

	public Job getJob();
	
	public void instantiate(JobRun jobRun, ApplicationContext applicationContext);
	
	public void runJob() throws TermServerScriptException;
	
	public String getReportName();

	public void setExclusions(List<Concept> exclusions);

	public void setReportManager(ReportManager reportManager);

	public JobRun getJobRun();
}
