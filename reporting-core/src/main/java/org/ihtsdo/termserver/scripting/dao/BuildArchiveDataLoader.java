package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;

@Service
public class BuildArchiveDataLoader extends AbstractDataLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildArchiveDataLoader.class);

	@Override
	public void download(File archive) throws TermServerScriptException {
		Path sourcePath = archive.toPath();
		Path targetPath = Path.of(getS3Manager().getResourceManager().getCachePath() + '/' + archive.getPath());
		downloadFromS3(sourcePath, targetPath);
	}

	@Autowired
	public void setConfig(BuildArchiveLoaderConfig config) {
		initS3Manager(config);
	}

	public static BuildArchiveDataLoader create() throws TermServerScriptException {
		LOGGER.info("Creating BuildArchiveDataLoader based on local properties");
		BuildArchiveDataLoader loader = new BuildArchiveDataLoader();
		loader.initS3Manager(new BuildArchiveLoaderConfig(), getConfigurationPrefix(BuildArchiveLoaderConfig.class));
		return loader;
	}
}
