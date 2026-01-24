package org.ihtsdo.termserver.scripting.dao;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ArchiveDataLoader implements DataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveDataLoader.class);

	private ArchiveLoaderConfig archiveLoaderConfig;

	@Value("${cloud.aws.region.static}")
	private String region;

	@Value("${aws.key}")
	private String awsKey;

	@Value("${aws.secretKey}")
	private String awsSecretKey;

	private static S3Manager s3Manager;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		LOGGER.info("ArchiveDataLoader initialised - SpringBoot configuration");
		if (awsKey == null) {
			LOGGER.info("ArchiveDataLoader - AWS Key missing?");
		} else if (awsKey.isEmpty()) {
			LOGGER.info("ArchiveDataLoader - AWS Key configured through EC2 instance");
		} else {
			LOGGER.info("ArchiveDataLoader using AWS Key: " + awsKey);
		}
	}

	@Override
	public void download (File archive) throws TermServerScriptException {
		if (s3Manager.isUseCloud()) {
			//Make sure the directory we're going to write to actually exists
			File parentDir = archive.getParentFile();
			if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
				throw new TermServerScriptException(
						"Failed to create directory for archive: " + parentDir.getAbsolutePath()
				);
			}

			try {
				ResourceManager resourceManager = s3Manager.getResourceManager();
				LOGGER.info("Downloading {} from S3", archive.getName());
				try (InputStream input = resourceManager.readResourceStream(archive.getName());
					OutputStream out = new FileOutputStream(archive);) {
					IOUtils.copy(input, out);
					LOGGER.info("Download complete");
				}
			} catch (Throwable  t) {
				final String msg = "Error when trying to download " + archive.getName() + " from S3 via: " +  archiveLoaderConfig;
				throw new TermServerScriptException(msg, t);
			}
		} else {
			LOGGER.info("ArchiveDataLoader set to local source. Will expect {} to be available.", archive);
		}
	}

	@Autowired
	public void setArchiveLoaderConfig(ArchiveLoaderConfig archiveLoaderConfig) {
		this.archiveLoaderConfig = archiveLoaderConfig;
		s3Manager = new S3Manager(archiveLoaderConfig, region, awsKey, awsSecretKey);
	}

	public void setArchiveLoaderConfig(ArchiveLoaderConfig archiveLoaderConfig, S3Manager s3Manager) {
		this.archiveLoaderConfig = archiveLoaderConfig;
		this.s3Manager = s3Manager;
	}

	public static ArchiveDataLoader create() throws TermServerScriptException {
		LOGGER.info("Creating ArchiveDataLoader based on local properties");
		ArchiveDataLoader loader = new ArchiveDataLoader();

		ArchiveLoaderConfig archiveLoaderConfig = new ArchiveLoaderConfig();
		s3Manager = new S3Manager(archiveLoaderConfig, getConfigurationPrefix());
		loader.setArchiveLoaderConfig(archiveLoaderConfig, s3Manager);
		return loader;
	}

	private static String getConfigurationPrefix() {
		return ArchiveLoaderConfig.class.getAnnotation(ConfigurationProperties.class).prefix();
	}

	public S3Manager getS3Manager() {
		return s3Manager;
	}
}
