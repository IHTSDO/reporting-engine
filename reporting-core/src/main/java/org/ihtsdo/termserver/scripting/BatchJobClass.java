package org.ihtsdo.termserver.scripting;

import org.snomed.otf.scheduler.domain.*;

public interface BatchJobClass extends JobClass {
	
	public void setStandardParameters(JobParameters param);
	
	public void recoverStandardParameter(JobRun run);
}
