package org.ihtsdo.authoring.scheduler.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.google.gdata.util.common.base.StringUtil;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.authoring.scheduler.api.configuration.ModuleStorageResourceConfig;
import org.ihtsdo.authoring.scheduler.api.mq.ActiveMQConnectionFactoryForAutoscaling;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
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

	@Bean
	public ResourceManager resourceManager(@Autowired ModuleStorageResourceConfig resourceConfiguration, @Autowired ResourceLoader cloudResourceLoader) {
		return new ResourceManager(resourceConfiguration, cloudResourceLoader);
	}

	@Bean
	public ModuleStorageCoordinator moduleStorageCoordinator(@Autowired ResourceManager resourceManager, @Value("${reporting.service.terminology.server.uri}") final String terminologyServerUrl) {
		if (StringUtils.isEmpty(terminologyServerUrl)) {
			throw new IllegalArgumentException("No value supplied for reporting.service.terminology.server.uri in application.properties file (or Consul)");
		}
		
		return switch (Objects.requireNonNull(getEnvironment(terminologyServerUrl))) {
			case "prod" -> ModuleStorageCoordinator.initProd(resourceManager);
			case "uat" -> ModuleStorageCoordinator.initUat(resourceManager);
			case "dev" -> ModuleStorageCoordinator.initDev(resourceManager);
			default -> null;
		};
	}

	private String getEnvironment(String terminologyServerUrl)  {
		URI uri;
		try {
			uri = new URI(terminologyServerUrl);
		} catch (URISyntaxException e) {
			System.out.println("Failed to detect environment. Error message: " + e.getMessage());
			return StringUtil.EMPTY_STRING;
		}
		String domain = uri.getHost();
		domain = domain.startsWith("www.") ? domain.substring(4) : domain;
		return (domain.contains("-") ? domain.substring(0, domain.indexOf("-")) : domain.substring(0, domain.indexOf("."))).toLowerCase();
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
