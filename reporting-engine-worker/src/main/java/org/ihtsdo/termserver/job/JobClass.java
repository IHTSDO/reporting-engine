package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobRun;

public interface JobClass {

	public void postInit(String subHierarchyStr) throws TermServerScriptException;

	public Job getJob();
	
	public void runJob(JobRun jobRun);
}
