package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.snomed.otf.scheduler.domain.*;

public class JobRunner implements Runnable {

	JobRun jobRun;
	ScheduleService service;
	
	JobRunner(ScheduleService service, JobRun jobRun) {
		this.service = service;
		this.jobRun = jobRun;
	}

	@Override
	public void run() {
		try {
			service.runJob(jobRun);
		} catch (BusinessServiceException e) {
			e.printStackTrace();
		}
	}

}
