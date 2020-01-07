package org.ihtsdo.termserver.job;

import org.snomed.otf.scheduler.domain.*;

public interface BatchJobClass extends JobClass {
	
	public void setStandardParameters(JobParameters param);
	
	public void recoverStandardParameter(JobRun run);
}
