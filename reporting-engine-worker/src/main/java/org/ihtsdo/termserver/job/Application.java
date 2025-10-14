package org.ihtsdo.termserver.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import org.ihtsdo.termserver.job.mq.ActiveMQConnectionFactoryForAutoscaling;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.TimeZone;

@SpringBootApplication
@ImportResource("classpath:services-context.xml")
@ComponentScan(basePackages = { "org.ihtsdo.termserver.job",
								"org.ihtsdo.termserver.scripting",
								"org.snomed.otf.scheduler.domain",
								"org.snomed.otf.script"})
@EnableJms
public class Application  {

	static TermServerScript job;
	
	@Bean
	public ObjectMapper objectMapper() {
		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

		final StdDateFormat df = new StdDateFormat();
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		objectMapper.setDateFormat(df);
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		return objectMapper;
	}

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
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
