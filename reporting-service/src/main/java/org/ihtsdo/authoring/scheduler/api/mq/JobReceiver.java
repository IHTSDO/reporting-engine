package org.ihtsdo.authoring.scheduler.api.mq;

import org.ihtsdo.authoring.scheduler.api.service.ScheduleService;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class JobReceiver {
	@Autowired
    ScheduleService service;

	@JmsListener(destination = "${reporting.service.queue.response}")
	public void receiveMessage(JobRun jobRun) {
		service.processResponse(jobRun);
	}
}
