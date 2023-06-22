package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class BuildArchiveDataLoader implements DataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildArchiveDataLoader.class);

	private StandAloneResourceConfig config;

	private S3Manager s3Manager;

	@Value("${cloud.aws.region.static}")
	private String region;

	@Value("${aws.key}")
	private String awsKey;

	@Value("${aws.secretKey}")
	private String awsSecretKey;

	public BuildArchiveDataLoader() {
	}

	public BuildArchiveDataLoader(StandAloneResourceConfig config, S3Manager s3Manager) {
		this.config = config;
		this.s3Manager = s3Manager;
	}

	@Override
	public void download (File archive) throws TermServerScriptException {
		Path targetFilePath = archive.toPath();
		Path sourceFilePath = targetFilePath.subpath(1, archive.toPath().getNameCount());

		TermServerScript.info(targetFilePath.toString()); //
		TermServerScript.info(sourceFilePath.toString()); //

		if (s3Manager.isUseCloud()) {
			try {
				// Create all directories if needed (no exception is thrown if some or all already exist)
				Files.createDirectories(targetFilePath.getParent());

				TermServerScript.info(targetFilePath.getParent().toString()); //

				ResourceManager resourceManager = s3Manager.getResourceManager();

				try (InputStream input = resourceManager.readResourceStream(sourceFilePath.toString());
					 OutputStream output = new FileOutputStream(archive)) {
					TermServerScript.info("Downloading " + sourceFilePath + " from S3");
					IOUtils.copy(input, output);
					TermServerScript.info("Download complete");
				}
			} catch (Throwable t) {
				final String msg = "Error when trying to download " + sourceFilePath + " from S3 via :" +  config;
				throw new TermServerScriptException(msg, t);
			}
		} else {
			LOGGER.info("ArchiveDataLoader set to local source. Will expect " + targetFilePath + " to be available.");
		}
	}

	@Autowired
	public void setConfig(BuildArchiveLoaderConfig config) {
		this.config = config;
		this.s3Manager = new S3Manager(config, region, awsKey, awsSecretKey);
	}

	public static BuildArchiveDataLoader create() throws TermServerScriptException {
		LOGGER.info("Creating BuildArchiveDataLoader based on local properties");

		StandAloneResourceConfig config = new BuildArchiveLoaderConfig();
		S3Manager s3Manager = new S3Manager(config, getConfigurationPrefix());

		return new BuildArchiveDataLoader(config, s3Manager);
	}

	private static String getConfigurationPrefix() {
		return BuildArchiveLoaderConfig.class.getAnnotation(ConfigurationProperties.class).prefix();
	}
}
