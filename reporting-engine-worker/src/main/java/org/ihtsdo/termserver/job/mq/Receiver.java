package org.ihtsdo.termserver.job.mq;

import org.ihtsdo.termserver.job.JobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import org.apache.commons.lang.StringUtils;

@Service
public class Receiver {
	
	@Autowired
	JobManager jobManager;

	private static final Logger LOGGER = LoggerFactory.getLogger(Receiver.class);

	@JmsListener(destination = "${reporting.service.queue.request}")
	public void receiveMessage(JobRun jobRun) {
		String cookieStart = "AuthToken Missing";
		if (!StringUtils.isEmpty(jobRun.getAuthToken()) && jobRun.getAuthToken().length() > 20) {
			cookieStart = jobRun.getAuthToken().substring(0,20);
		}
		LOGGER.info("Received request to run {} with parameters: {} and authToken starting: {}", jobRun, jobRun.getParameters(), cookieStart);
		jobManager.run(jobRun);
	}

}
