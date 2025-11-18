package org.ihtsdo.authoring.scheduler.api.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQConnectionFactoryCustomizer;

public class ActiveMQConnectionFactoryForAutoscaling implements ActiveMQConnectionFactoryCustomizer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQConnectionFactoryForAutoscaling.class);

	@Override
	public void customize(ActiveMQConnectionFactory factory) {
		ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
		//Autoscaling only effective if we consume one message at a time.
		prefetchPolicy.setQueuePrefetch(0);
		factory.setPrefetchPolicy(prefetchPolicy);
		LOGGER.info("Prefetch policy set to '0' for autoscaling");
	}
}
