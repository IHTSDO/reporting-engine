package org.ihtsdo.authoring.scheduler.api.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;

public class JobRunner implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(JobRunner.class);

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
			LOGGER.error("Exception encountered",e);
		}
	}

}
