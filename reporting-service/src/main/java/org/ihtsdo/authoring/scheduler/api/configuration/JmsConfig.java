package org.ihtsdo.authoring.scheduler.api.configuration;

import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.activemq.autoconfigure.ActiveMQConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JmsConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(JmsConfig.class);

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		// Must stay in step with the equivalent converter in reporting-engine-worker's
		// WorkerJmsConfig, which carries the rationale for the Jackson 2 defaults and for leaving
		// FAIL_ON_UNKNOWN_PROPERTIES disabled. Change both or neither.
		JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(
				JsonMapper.builderWithJackson2Defaults()
						.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
						.build());
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

	@Bean
	public ActiveMQConnectionFactoryCustomizer autoScalingFactory() {
		return factory -> {
			ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
			//Autoscaling only effective if we consume one message at a time.
			prefetchPolicy.setQueuePrefetch(0);
			factory.setPrefetchPolicy(prefetchPolicy);
			LOGGER.info("Prefetch policy set to '0' for autoscaling");
		};
	}
}
