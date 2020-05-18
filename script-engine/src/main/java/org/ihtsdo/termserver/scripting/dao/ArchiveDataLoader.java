package org.ihtsdo.termserver.scripting.dao;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ArchiveDataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceDataLoader.class);

	private ArchiveLoaderConfig archiveLoaderConfig;

	@Value("${cloud.aws.region.static}")
	private String region;

	@Value("${aws.key}")
	private String awsKey;

	@Value("${aws.secretKey}")
	private String awsSecretKey;

	@Value("${archives.useCloud}")
	private String useCloudStr;

	private static S3Manager s3Manager;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		TermServerScript.info("ArchiveDataLoader initialised - SpringBoot configuration");
		if (awsKey == null) {
			TermServerScript.info("ArchiveDataLoader - AWS Key missing?");
		} else if (awsKey.isEmpty()) {
			TermServerScript.info("ArchiveDataLoader - AWS Key configured through EC2 instance");
		} else {
			TermServerScript.info("ArchiveDataLoader using AWS Key: " + awsKey);
		}
	}

	public void download (File archive) throws TermServerScriptException {
		if (s3Manager.isUseCloud()) {
			try {
				ResourceManager resourceManager = s3Manager.getResourceManager();

				try (InputStream input = resourceManager.readResourceStream(archive.getName());
					OutputStream out = new FileOutputStream(archive);) {
					TermServerScript.info("Downloading " + archive.getName() + " from S3");
					IOUtils.copy(input, out);
					TermServerScript.info("Download complete");
				}
			} catch (Throwable  t) {
				final String msg = "Error when trying to download " + archive.getName() + " from S3 via :" +  archiveLoaderConfig;
				throw new TermServerScriptException(msg, t);
			}
		} else {
			LOGGER.info("ArchiveDataLoader set to local source. Will expect " + archive + " to be available.");
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
}
