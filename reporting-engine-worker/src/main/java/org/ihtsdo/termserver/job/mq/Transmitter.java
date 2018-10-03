package org.ihtsdo.termserver.job.mq;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobMetadata;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Transmitter {
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Value("${schedule.manager.queue.response}")
	String responseQueueName;
	
	@Value("${schedule.manager.queue.metadata}")
	String metadataQueueName;
	
	ExecutorService executorService;
	
	@PostConstruct
	public void init() {
		executorService = Executors.newCachedThreadPool();
	}
	
	public void send (JobRun jobRun) {
		//We'll take out the authentication since it's not required by the client
		//Modify a clone as the original is still needed elsewhere!
		JobRun clone = jobRun.clone();
		clone.setAuthToken(null);

		//Transmit in a new thread so that we receive a separate transaction.   Otherwise the 'running' status
		//won't be sent until the job is complete
		executorService.execute(() -> {
				logger.info("Transmitting response:" + jobRun);
				jmsTemplate.convertAndSend(responseQueueName, clone);
		});
	}
	
	public void send (JobMetadata metadata) {
		logger.info("Transmitting metadata for " + metadata.getJobTypes().size() + " job types");
		jmsTemplate.convertAndSend(metadataQueueName, metadata);
	}

}
