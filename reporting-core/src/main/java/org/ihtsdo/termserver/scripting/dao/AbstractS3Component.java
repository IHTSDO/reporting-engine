package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common support for anything that fetches files from S3 via a StandAloneResourceConfig,
 * which all need to work in two contexts: as a SpringBoot managed bean (autowired,
 * credentials from application properties) or standalone eg from a script's main method
 * (created via a static create() method, credentials from local properties).
 */
public abstract class AbstractS3Component {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractS3Component.class);

	@Value("${cloud.aws.region.static}")
	protected String region;

	@Value("${aws.key}")
	protected String awsKey;

	@Value("${aws.secretKey}")
	protected String awsSecretKey;

	protected S3Manager s3Manager;

	public S3Manager getS3Manager() {
		return s3Manager;
	}

	/** Spring-managed usage: credentials come from application properties, already injected above. */
	protected void initS3Manager(StandAloneResourceConfig config) {
		s3Manager = new S3Manager(config, region, awsKey, awsSecretKey);
	}

	/** Standalone usage: credentials are loaded from local properties via the given configuration prefix. */
	protected void initS3Manager(StandAloneResourceConfig config, String configurationPrefix) throws TermServerScriptException {
		s3Manager = new S3Manager(config, configurationPrefix);
	}

	protected static String getConfigurationPrefix(Class<?> configurationClass) {
		return configurationClass.getAnnotation(ConfigurationProperties.class).prefix();
	}

	/**
	 * Copy a single object from S3 to a local file, gated on the config's useCloud setting.
	 * If tolerateMissing is true, a missing S3 object is logged and skipped rather than failing.
	 */
	protected void copyFromS3(Path sourcePath, Path targetPath, boolean tolerateMissing) throws TermServerScriptException {
		if (!s3Manager.isUseCloud()) {
			LOGGER.info("{} set to local source. Will expect {} to be available.", getClass().getSimpleName(), targetPath);
			return;
		}

		try {
			Files.createDirectories(targetPath.getParent());
			ResourceManager resourceManager = s3Manager.getResourceManager();
			try (InputStream input = tolerateMissing
						? resourceManager.readResourceStreamOrNullIfNotExists(sourcePath.toString())
						: resourceManager.readResourceStream(sourcePath.toString())) {
				if (input == null) {
					LOGGER.info("{} not found in S3, skipping", sourcePath);
					return;
				}
				try (OutputStream output = new FileOutputStream(targetPath.toFile())) {
					LOGGER.info("Downloading {} from S3", sourcePath);
					IOUtils.copy(input, output);
					LOGGER.info("Download complete");
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Error when trying to download " + sourcePath
					+ " from S3 via: " + s3Manager.getStandAloneResourceConfig(), e);
		}
	}
}
