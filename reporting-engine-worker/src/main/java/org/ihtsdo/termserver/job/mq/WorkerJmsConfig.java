package org.ihtsdo.termserver.job.mq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WorkerJmsConfig {

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jackson2CompatibleMapper());
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

	/**
	 * Jackson 3 changes several serialisation defaults - ISO-8601 dates instead of epoch millis, and
	 * alphabetical instead of declaration property order. The service-alert queue is consumed outside
	 * this repo, so Jackson 2's defaults are reproduced here to keep payloads byte-identical to what
	 * MappingJackson2MessageConverter produced. Drop this mapper once every consumer is known to
	 * tolerate the Jackson 3 output.
	 * <p>
	 * FAIL_ON_UNKNOWN_PROPERTIES must stay off: it is a Jackson library default that
	 * MappingJackson2MessageConverter disabled, and re-enabling it would send any message carrying a
	 * field this side does not know yet - a newer peer mid-rollout - to the DLQ.
	 */
	private static JsonMapper jackson2CompatibleMapper() {
		return JsonMapper.builderWithJackson2Defaults()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build();
	}

	@Bean
	public ActiveMQConnectionFactoryForAutoscaling autoScalingFactory() {
		return new ActiveMQConnectionFactoryForAutoscaling();
	}
}
