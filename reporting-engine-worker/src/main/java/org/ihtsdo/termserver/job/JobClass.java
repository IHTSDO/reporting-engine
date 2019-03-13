package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobRun;

public interface JobClass {
	
	public void postInit() throws TermServerScriptException;

	public Job getJob();
	
	//TODO Come up with distinct names here.  The first version is more 
	//like a setup, it calls init.  The 2nd is the actual report or job itself
	//running as a fully configured object
	public void instantiate(JobRun jobRun);
	
	public void runJob() throws TermServerScriptException;
}
