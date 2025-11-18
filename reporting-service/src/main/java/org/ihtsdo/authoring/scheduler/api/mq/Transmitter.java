package org.ihtsdo.authoring.scheduler.api.mq;

import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Transmitter {
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
	@Value("${reporting.service.queue.request}")
	String requestQueueName;
	
	public void send (JobRun run) {
		jmsTemplate.convertAndSend(requestQueueName, run);
	}

}
