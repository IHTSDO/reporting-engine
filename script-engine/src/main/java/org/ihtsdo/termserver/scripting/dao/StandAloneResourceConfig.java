package org.ihtsdo.termserver.scripting.dao;

import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.exception.TermServerScriptException;

import java.util.Arrays;

/**
 * This is needed when we're not running as a Spring Boot application and don't 
 * have access to Autowired and all that goodness
 */
public class StandAloneResourceConfig extends ResourceConfiguration {

	private enum CONFIGURATION {
		READ_ONLY			("readonly"),
		USE_CLOUD			("useCloud"),
		LOCAL_PATH			("local.path"),
		CLOUD_BUCKET_NAME	("cloud.bucketName"),
		CLOUD_PATH			("cloud.path");

		private String value;

		CONFIGURATION(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	public void init(String prefix) throws TermServerScriptException {
		LocalProperties properties = new LocalProperties(prefix);
		if (!isConfigurationValid(properties)) {
			throw new TermServerScriptException("Check application.properties for correct S3 config: "
					+ CONFIGURATION.values());
		}

		setReadonly(properties.getBooleanProperty(CONFIGURATION.READ_ONLY.value));
		setUseCloud(properties.getBooleanProperty(CONFIGURATION.USE_CLOUD.value));
		setLocal(new Local(properties.getProperty(CONFIGURATION.LOCAL_PATH.value)));
		setCloud(new Cloud(properties.getProperty(CONFIGURATION.CLOUD_BUCKET_NAME.value),
				properties.getProperty(CONFIGURATION.CLOUD_PATH.value)));
	}

	private boolean isConfigurationValid(LocalProperties properties) {
		return Arrays.stream(CONFIGURATION.values())
				.allMatch(aConfig -> {
					try {
						Strings.isNotEmpty(properties.getProperty(aConfig.getValue()));
					} catch (TermServerScriptException e) {
						return false;
					}
					return true;
				});
	}
}
