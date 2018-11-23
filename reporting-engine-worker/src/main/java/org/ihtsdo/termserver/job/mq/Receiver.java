package org.ihtsdo.termserver.job.mq;

import org.ihtsdo.termserver.job.JobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class Receiver {
	
	@Autowired
	JobManager jobManager;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@JmsListener(destination = "${schedule.manager.queue.request}")
	public void receiveMessage(JobRun jobRun) {
		logger.info("Received request to run {} with parameters ", jobRun, jobRun.getParameters());
		jobManager.run(jobRun);
	}

}
