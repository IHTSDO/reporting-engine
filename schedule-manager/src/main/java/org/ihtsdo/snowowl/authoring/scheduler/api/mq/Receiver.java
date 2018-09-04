package org.ihtsdo.snowowl.authoring.scheduler.api.mq;

import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class Receiver {
	
	@JmsListener(destination = "${schedule.manager.queue.response}")
	public void receiveMessage(JobRun jobRun) {
		System.out.println("Received <" + jobRun + ">");
	}

}
