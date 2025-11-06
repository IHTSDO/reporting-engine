package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.LocalProperties;
import org.snomed.otf.script.dao.SimpleStorageResourceLoader;
import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.core.io.ResourceLoader;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

public class S3Manager {
	private static final Logger LOGGER = LoggerFactory.getLogger(S3Manager.class);

	private String region;
	private String awsKey;
	private String awsSecretKey;

	private StandAloneResourceConfig standAloneResourceConfig;
	private SimpleStorageResourceLoader simpleStorageResourceLoader;
	private ResourceManager resourceManager;

	public S3Manager(StandAloneResourceConfig standAloneResourceConfig) {
		this.standAloneResourceConfig = standAloneResourceConfig;
	}

	public S3Manager(StandAloneResourceConfig standAloneResourceConfig,
					 String region,
					 String awsKey,
					 String awsSecretKey) {
		this(standAloneResourceConfig);
		this.region = region;
		this.awsKey = awsKey;
		this.awsSecretKey = awsSecretKey;
	}

	public S3Manager(StandAloneResourceConfig standAloneResourceConfig,
					 String configurationPrefix) throws TermServerScriptException {
		this(standAloneResourceConfig);

		// load properties if local
		if (!StringUtils.isEmpty(configurationPrefix)) {
			loadProperties(configurationPrefix);
		}
	}

	public ResourceManager getResourceManager() throws TermServerScriptException {

		if (resourceManager != null) {
			return resourceManager;
		}
		synchronized (this) {
			if (resourceManager != null) {
				return resourceManager;
			}
			try {
				S3Client s3Client;
				if (StringUtils.isEmpty(awsKey)) {
					s3Client = S3Client.builder()
							.region(DefaultAwsRegionProviderChain.builder().build().getRegion())
							.build();
					LOGGER.info("Connecting to S3 with EC2 environment configured credentials");
				} else {
					s3Client = S3Client.builder()
							.region(Region.of(region))
							.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsKey, awsSecretKey)))
							.build();
					LOGGER.info("Connecting to S3 with locally specified account: {}", awsKey);
					//AWS will still attempt to connect locally to discover its credentials, so let's only
					//do debugging at "WARN"   See logback.xml file for configuration
				}
				simpleStorageResourceLoader = new SimpleStorageResourceLoader(s3Client);
				simpleStorageResourceLoader.setTaskExecutor(task -> {
				});
				resourceManager = new ResourceManager(standAloneResourceConfig, simpleStorageResourceLoader);
			} catch (Throwable t) {
				final String msg = "Error when trying get the resource manager for S3 via :" + standAloneResourceConfig;
				throw new TermServerScriptException(msg, t);
			}
		}
		return resourceManager;
	}

	public boolean isUseCloud() {
		return standAloneResourceConfig.isUseCloud();
	}

	private boolean isConfigurationValid() {
		if (StringUtils.isEmpty(region) ||
				StringUtils.isEmpty(awsKey) ||
				StringUtils.isEmpty(awsSecretKey)) {
			return false;
		}
		return true;
	}

	private void loadProperties(String configurationPrefix) throws TermServerScriptException {
		try {
			LocalProperties properties = new LocalProperties(null);
			this.region = properties.getProperty("cloud.aws.region.static");
			this.awsKey = properties.getProperty("aws.key");
			this.awsSecretKey = properties.getProperty("aws.secretKey");

			if (standAloneResourceConfig != null) {
				standAloneResourceConfig.init(configurationPrefix);
			}
		} catch (Exception e) {
			String message = "Unable to create using local configuration. " +
					"Check availability of application-local.properties";
			throw new TermServerScriptException (message,e);
		}
	}

	// For debugging
	public StandAloneResourceConfig getStandAloneResourceConfig() {
		return standAloneResourceConfig;
	}

	public ResourceLoader getResourceLoader() throws TermServerScriptException {
		if (simpleStorageResourceLoader == null) {
			getResourceManager();
		}
		return simpleStorageResourceLoader;
	}
}
