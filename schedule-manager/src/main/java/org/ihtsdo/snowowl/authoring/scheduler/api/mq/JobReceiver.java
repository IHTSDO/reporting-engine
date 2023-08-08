package org.ihtsdo.snowowl.authoring.scheduler.api.mq;

import org.ihtsdo.snowowl.authoring.scheduler.api.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class JobReceiver {
	@Autowired
	ScheduleService service;

	@JmsListener(destination = "${schedule.manager.queue.response}")
	public void receiveMessage(JobRun jobRun) {
		service.processResponse(jobRun);
	}
}
