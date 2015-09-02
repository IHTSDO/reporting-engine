package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArbitraryJsonService {

	public static final String UTF_8 = "UTF-8";

	private File baseDirectory;
	private Logger logger = LoggerFactory.getLogger(ArbitraryJsonService.class);

	public ArbitraryJsonService() {
		this.baseDirectory = new File("resources/org.ihtsdo.snowowl.authoring.single.api");
	}

	public void write(String path, String json) throws IOException {
		Files.write(getFile(path), json.getBytes(UTF_8));
	}

	public String read(String path) throws IOException {
		return new String(Files.readAllBytes(getFile(path)), UTF_8);
	}

	private Path getFile(String relativePath) {
		File file = new File(baseDirectory, relativePath);
		File parentDirectory = file.getParentFile();
		if (!parentDirectory.isDirectory()) {
			if (!parentDirectory.mkdirs()) {
				logger.error("Could not create directory " + parentDirectory.getAbsolutePath());
			}
		}
		return file.toPath();
	}
}
