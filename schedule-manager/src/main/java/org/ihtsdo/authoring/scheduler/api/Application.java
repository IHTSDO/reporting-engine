package org.ihtsdo.authoring.scheduler.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import org.ihtsdo.authoring.scheduler.api.mq.ActiveMQConnectionFactoryForAutoscaling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import org.springframework.boot.autoconfigure.domain.EntityScan;

import java.util.TimeZone;

@SpringBootApplication
@ImportResource("classpath:services-context.xml")
@EntityScan(basePackages="org.snomed.otf.scheduler.domain")
@EnableJms
public class Application {

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

/*	@Bean
	public HttpMessageConverters customConverters() {
		final StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(Charsets.UTF_8);
		stringConverter.setWriteAcceptCharset(false);

		final MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
		jacksonConverter.setObjectMapper(objectMapper());

		return new HttpMessageConverters(
				stringConverter,
				new ByteArrayHttpMessageConverter(),
				new ResourceHttpMessageConverter(),
				jacksonConverter);
	}*/

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
