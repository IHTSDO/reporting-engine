package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.job.mq.ActiveMQConnectionFactoryForAutoscaling;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootApplication
@ImportResource("classpath:services-context.xml")
@ComponentScan(basePackages = { "org.ihtsdo.termserver.job",
								"org.ihtsdo.termserver.scripting",
								"org.snomed.otf.scheduler.domain",
								"org.snomed.otf.script"})
@EnableJms
public class Application  {

	static TermServerScript job;
	
	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		// Jackson 3 changes several serialisation defaults - ISO-8601 dates instead of epoch
		// millis, and alphabetical instead of declaration property order. The service-alert
		// queue is consumed outside this repo, so Jackson 2's defaults are reproduced here to
		// keep payloads byte-identical to what MappingJackson2MessageConverter produced. Drop
		// this mapper once every consumer is known to tolerate the Jackson 3 output.
		// FAIL_ON_UNKNOWN_PROPERTIES must stay off: it is a Jackson library default that
		// MappingJackson2MessageConverter disabled, and re-enabling it would send any message
		// carrying a field this side does not know yet - a newer peer mid-rollout - to the DLQ.
		JsonMapper jsonMapper = JsonMapper.builderWithJackson2Defaults()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build();
		JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}
	
	@Bean
	public ActiveMQConnectionFactoryForAutoscaling autoScalingFactory() {
		return new ActiveMQConnectionFactoryForAutoscaling();
	}

	public static void main(String[] args) {
		new SpringApplicationBuilder(Application.class)
		.web(WebApplicationType.NONE) // .REACTIVE, .SERVLET
		.run(args);
	}
	
}
