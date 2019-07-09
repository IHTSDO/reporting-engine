package org.ihtsdo.termserver.job.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQConnectionFactoryCustomizer;

public class ActiveMQConnectionFactoryForAutoscaling implements ActiveMQConnectionFactoryCustomizer {

	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Override
	public void customize(ActiveMQConnectionFactory factory) {
		ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
		//Autoscaling only effective if we consume one message at a time.
		prefetchPolicy.setQueuePrefetch(1);
		factory.setPrefetchPolicy(prefetchPolicy);
		logger.info("Prefetch policy set to '1' for autoscaling");
	}
}
