package org.ihtsdo.authoring.scheduler.api;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.authoring.scheduler.api.configuration.ModuleStorageResourceConfig;
import org.ihtsdo.authoring.scheduler.api.mq.ActiveMQConnectionFactoryForAutoscaling;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@SpringBootApplication
@ImportResource("classpath:services-context.xml")
@EntityScan(basePackages="org.snomed.otf.scheduler.domain")
@EnableJms
public class Application {

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		// Jackson 3 changes several serialisation defaults - ISO-8601 dates instead of epoch
		// millis, and alphabetical instead of declaration property order. Kept in step with
		// reporting-engine-worker, which reproduces Jackson 2's defaults for the externally
		// consumed service-alert queue.
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

	@Bean
	public ResourceManager resourceManager(ModuleStorageResourceConfig resourceConfiguration, ResourceLoader cloudResourceLoader) {
		return new ResourceManager(resourceConfiguration, cloudResourceLoader);
	}

	@Bean
	public ModuleStorageCoordinator moduleStorageCoordinator(ResourceManager resourceManager, @Value("${reporting.service.terminology.server.uri}") final String terminologyServerUrl) {
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
			return StringUtils.EMPTY;
		}
		String domain = uri.getHost();
		domain = domain.startsWith("www.") ? domain.substring(4) : domain;
		return (domain.contains("-") ? domain.substring(0, domain.indexOf("-")) : domain.substring(0, domain.indexOf("."))).toLowerCase();
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
