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
		//Transmit in a new thread so that we receive a separate transaction.   Otherwise the 'running' status
		//won't be sent until the job is complete
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run(){
				logger.info("Transmitting response:" + jobRun);
				//We'll take out the authentication since it's not required by the client
				//Modify a clone as the original is still needed elsewhere!
				JobRun clone = jobRun.clone();
				clone.setAuthToken(null);
				jmsTemplate.convertAndSend(responseQueueName, clone);
			}
		});
		thread.start();
	}
	
	public void send (JobMetadata metadata) {
		logger.info("Transmitting metadata for " + metadata.getJobTypes().size() + " job types");
		jmsTemplate.convertAndSend(metadataQueueName, metadata);
	}

}
