package org.ihtsdo.snowowl.authoring.scheduler.api.mq;


import org.ihtsdo.snowowl.authoring.scheduler.api.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class MetadataReceiver {
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	ScheduleService service;
	
	@JmsListener(destination = "${schedule.manager.queue.metadata}")
	public void receiveMessage(JobMetadata metadata) {
		logger.info("Received metadata for {} jobs", metadata.getJobTypes().size());
		service.processMetadata(metadata);
	}

}
