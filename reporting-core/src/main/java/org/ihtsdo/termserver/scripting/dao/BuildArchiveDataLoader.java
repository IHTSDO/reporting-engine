package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class BuildArchiveDataLoader implements DataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildArchiveDataLoader.class);

	private BuildArchiveLoaderConfig config;

	@Override
	public void download (File archive) throws TermServerScriptException {
		LOGGER.info("Create S3 manager for download of build archive via: {}", config);
		S3Manager s3Manager = new S3Manager(config);

		Path sourcePath = archive.toPath();
		Path targetPath = Path.of(s3Manager.getResourceManager().getCachePath() + '/' + archive.getPath());

		if (s3Manager.isUseCloud()) {
			try {
				// Create all directories if needed (no exception is thrown if some or all already exist)
				Files.createDirectories(targetPath.getParent());
				ResourceManager resourceManager = s3Manager.getResourceManager();

				try (InputStream input = resourceManager.readResourceStream(sourcePath.toString());
				     OutputStream output = new FileOutputStream(targetPath.toString())) {
					LOGGER.info("Downloading {} from S3", sourcePath);
					IOUtils.copy(input, output);
					LOGGER.info("Download complete");
				}
			} catch (Exception t) {
				throw new TermServerScriptException("Error when trying to download " + sourcePath + " from S3 via: " + s3Manager.getStandAloneResourceConfig(), t);
			}
		} else {
			LOGGER.info("BuildArchiveDataLoader set to local source. Will expect {} to be available.", targetPath);
		}
	}

	@Autowired
	public void setConfig(BuildArchiveLoaderConfig config) {
		this.config = config;
	}

	public static BuildArchiveDataLoader create() throws TermServerScriptException {
		LOGGER.info("Creating BuildArchiveDataLoader based on local properties");
		BuildArchiveDataLoader loader = new BuildArchiveDataLoader();

		BuildArchiveLoaderConfig config = new BuildArchiveLoaderConfig();
		config.init(getConfigurationPrefix(BuildArchiveLoaderConfig.class));
		loader.setConfig(config);

		return loader;
	}

	private static String getConfigurationPrefix(Class<?> configurationClass) {
		return configurationClass.getAnnotation(ConfigurationProperties.class).prefix();
	}
}
