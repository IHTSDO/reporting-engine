package org.ihtsdo.termserver.job.mq;


import java.util.concurrent.*;

import javax.annotation.PostConstruct;

import org.ihtsdo.termserver.job.JobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Transmitter {
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
	@Autowired
	private JobManager jobManager;
	
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
	
	public void send (JobRun run) {
		//We'll take out the authentication since it's not required by the client
		//Modify a clone as the original is still needed elsewhere!
		JobRun clone = run.clone();
		clone.setAuthToken(null);
		
		//We also need to only return parameters that the job indicated it can handle
		//Can only do this for jobs we know about however!
		Job job = jobManager.getJob(run.getJobName());
		if (run.getParameters() != null && job != null) {
			for (String key : run.getParameters().keySet()) {
				if (job.getParameters().get(key) == null) {
					run.getParameters().remove(key);
				}
			}
		}

		//Transmit in a new thread so that we receive a separate transaction.   Otherwise the 'running' status
		//won't be sent until the job is complete
		executorService.execute(() -> {
				logger.info("Transmitting response:" + run);
				jmsTemplate.convertAndSend(responseQueueName, clone);
		});
	}
	
	public void send (JobMetadata metadata) {
		logger.info("Transmitting metadata for " + metadata.getJobTypes().size() + " job types:");
		for (JobType type : metadata.getJobTypes()) {
			logger.info ("  {}:", type.getName());
			for (JobCategory category : type.getCategories()) {
				logger.info("\t{} : {} jobs", category.getName(), category.getJobs().size());
			}
		}
		jmsTemplate.convertAndSend(metadataQueueName, metadata);
	}

}
