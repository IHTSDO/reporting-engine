package org.ihtsdo.termserver.job.mq;

import org.ihtsdo.termserver.job.JobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class Receiver {
	
	@Autowired
	JobManager jobManager;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@JmsListener(destination = "${schedule.manager.queue.request}")
	public void receiveMessage(JobRun jobRun) {
		String cookieStart = "AuthToken Missing";
		if (!StringUtils.isEmpty(jobRun.getAuthToken()) && jobRun.getAuthToken().length() > 16) {
			cookieStart = jobRun.getAuthToken().substring(0,15);
		}
		logger.info("Received request to run {} with parameters: {} and authToken starting: {}", jobRun, jobRun.getParameters(), cookieStart);
		jobManager.run(jobRun);
	}

}
