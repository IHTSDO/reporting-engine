package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;

@Service
public class MscDataLoader extends AbstractDataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(MscDataLoader.class);

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		LOGGER.info("MscDataLoader initialised - SpringBoot configuration");
		if (awsKey == null) {
			LOGGER.info("MscDataLoader - AWS Key missing?");
		} else if (awsKey.isEmpty()) {
			LOGGER.info("MscDataLoader - AWS Key configured through EC2 instance");
		} else {
			LOGGER.info("MscDataLoader using AWS Key: {}", awsKey);
		}
	}

	@Override
	public void download(File archive) throws TermServerScriptException {
		downloadFromS3(Path.of(archive.getName()), archive.toPath());
	}

	@Autowired
	public void setConfig(MscLoaderConfig config) {
		initS3Manager(config);
	}

	public static MscDataLoader create() throws TermServerScriptException {
		LOGGER.info("Creating MscDataLoader based on local properties");
		MscDataLoader loader = new MscDataLoader();
		loader.initS3Manager(new MscLoaderConfig(), getConfigurationPrefix(MscLoaderConfig.class));
		return loader;
	}
}
