package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class BuildArchiveDataLoader implements DataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildArchiveDataLoader.class);

	private StandAloneResourceConfig buildArchiveConfig;

	private StandAloneResourceConfig publishedArchiveConfig;

	public BuildArchiveDataLoader() {
	}

	public BuildArchiveDataLoader(BuildArchiveLoaderConfig buildArchiveConfig, ArchiveLoaderConfig publishedArchiveConfig) {
		this.buildArchiveConfig = buildArchiveConfig;
		this.publishedArchiveConfig = publishedArchiveConfig;
	}

	@Override
	public void download (File archive) throws TermServerScriptException {
		Path targetFilePath = archive.toPath();
		Path sourceFilePath = archive.toPath().subpath(1, archive.toPath().getNameCount()); // remove first name from the path

		LOGGER.debug("Target filepath: {}", targetFilePath);
		LOGGER.debug("Source filepath: {}", sourceFilePath);

		S3Manager s3Manager;

		if (sourceFilePath.getNameCount() > 1) {
			// Download build archive
			s3Manager = new S3Manager(buildArchiveConfig);
			LOGGER.info("Create S3 manager for download of build archive via: {}", buildArchiveConfig);
		} else {
			// Download published dependency archive
			s3Manager = new S3Manager(publishedArchiveConfig);
			LOGGER.info("Create S3 manager for download of published archive via: {}", publishedArchiveConfig);
		}

		LOGGER.debug("isUseCloud = {}", s3Manager.isUseCloud());

		if (s3Manager.isUseCloud()) {
			try {
				// Create all directories if needed (no exception is thrown if some or all already exist)
				Files.createDirectories(targetFilePath.getParent());
				ResourceManager resourceManager = s3Manager.getResourceManager();

				try (InputStream input = resourceManager.readResourceStream(sourceFilePath.toString());
					 OutputStream output = new FileOutputStream(archive)) {
					LOGGER.info("Downloading {} from S3", sourceFilePath);
					IOUtils.copy(input, output);
					LOGGER.info("Download complete");
				}
			} catch (Throwable t) {
				throw new TermServerScriptException("Error when trying to download " + sourceFilePath + " from S3 via: " + s3Manager.getStandAloneResourceConfig(), t);
			}
		} else {
			LOGGER.info("ArchiveDataLoader set to local source. Will expect {} to be available.", targetFilePath);
		}
	}

	@Autowired
	public void setConfig(BuildArchiveLoaderConfig buildArchiveConfig, ArchiveLoaderConfig publishedArchiveConfig) {
		this.buildArchiveConfig = buildArchiveConfig;
		this.publishedArchiveConfig = publishedArchiveConfig;
	}

	public static BuildArchiveDataLoader create() throws TermServerScriptException {
		LOGGER.info("Creating BuildArchiveDataLoader based on local properties");

		BuildArchiveLoaderConfig buildArchiveConfig = new BuildArchiveLoaderConfig();
		buildArchiveConfig.init(getConfigurationPrefix(BuildArchiveLoaderConfig.class));

		ArchiveLoaderConfig publishedArchiveConfig = new ArchiveLoaderConfig();
		publishedArchiveConfig.init(getConfigurationPrefix(ArchiveLoaderConfig.class));

		return new BuildArchiveDataLoader(buildArchiveConfig, publishedArchiveConfig);
	}

	private static String getConfigurationPrefix(Class<?> configurationClass) {
		return configurationClass.getAnnotation(ConfigurationProperties.class).prefix();
	}
}
