package org.ihtsdo.authoring.scheduler.api.mq;


import org.ihtsdo.authoring.scheduler.api.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class MetadataReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetadataReceiver.class);

	@Autowired
    ScheduleService service;
	
	@JmsListener(destination = "${reporting.service.queue.metadata}")
	public void receiveMessage(JobMetadata metadata) {
		LOGGER.info("Received metadata for {} jobs", metadata.getJobTypes().size());
		service.processMetadata(metadata);
	}

}
