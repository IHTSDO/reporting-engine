package org.ihtsdo.termserver.job.mq;


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
	
	public void send (JobRun jobRun) {
		logger.info("Transmitting response:" + jobRun);
		jmsTemplate.convertAndSend(responseQueueName, jobRun);
	}
	
	public void send (JobMetadata metadata) {
		logger.info("Transmitting metadata for " + metadata.getJobs().size() + " jobs");
		jmsTemplate.convertAndSend(metadataQueueName, metadata);
	}

}
