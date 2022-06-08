package org.ihtsdo.termserver.job.mq;

import java.util.*;
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
	
	public static int MAX_TEXT_LENGTH = 65000;
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Value("${schedule.manager.queue.response}")
	String responseQueueName;
	
	@Value("${schedule.manager.queue.metadata}")
	String metadataQueueName;
	
	@Value("${reporting.worker.queue.service-alert}")
	String serviceAlertQueueName;
	
	ExecutorService executorService;
	
	@PostConstruct
	public void init() {
		executorService = Executors.newCachedThreadPool();
	}
	
	public void send (JobManager jobManager, JobRun run) {
		//We'll take out the authentication since it's not required by the client
		//Modify a clone as the original is still needed elsewhere!
		JobRun clone = run.clone();
		clone.setAuthToken(null);
		
		//RP-594 We might have more information than we can digest in the debugInfo
		String debugInfo = run.getDebugInfo();
		if (debugInfo != null) {
			logger.warn("Full debug message truncated to 65K characters\n {}", debugInfo);
			debugInfo = debugInfo.substring(0, Math.min(debugInfo.length(), MAX_TEXT_LENGTH)) + "...[truncated at source]";
			run.setDebugInfo(debugInfo);
		}
		
		//We also need to only return parameters that the job indicated it can handle
		//Can only do this for jobs we know about however!
		//We'll also re-assert the specified parameter order at this time, in case it's been lost
		Job job = jobManager.getJob(run.getJobName());
		if (run.getParameters() != null && job != null) {
			//Ensure all jobs allow the project to be chosen
			Set<String> paramKeys = new HashSet<>(run.getParameters().keySet());
			for (String key : paramKeys) {
				if (job.getParameters().get(key) == null) {
					run.getParameters().remove(key);
				} else {
					Integer order = job.getParameters().get(key).getDisplayOrder();
					run.getParameters().get(key).setDisplayOrder(order);
				}
			}
		}

		//Transmit in a new thread so that we receive a separate transaction.   Otherwise the 'running' status
		//won't be sent until the job is complete
		executorService.execute(() -> {
				logger.info("Transmitting response: {}", run);
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
	
	public void send (SnomedServiceException serviceAlert) {
		logger.info("Transmitting service alert: " + serviceAlert);
		jmsTemplate.convertAndSend(serviceAlertQueueName, serviceAlert);
	}

}
