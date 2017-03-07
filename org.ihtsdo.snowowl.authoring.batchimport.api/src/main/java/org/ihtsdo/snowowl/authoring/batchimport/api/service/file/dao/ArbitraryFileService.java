package org.ihtsdo.snowowl.authoring.batchimport.api.service.file.dao;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArbitraryFileService {

	public static final String UTF_8 = "UTF-8";

	protected File baseDirectory;
	protected Logger logger = LoggerFactory.getLogger(ArbitraryFileService.class);

	public ArbitraryFileService() {
		this.baseDirectory = new File("resources/org.ihtsdo.snowowl.authoring.single.api");
	}

	public void write(String path, String data) throws IOException {
		Files.write(getFile(path), data.getBytes(UTF_8));
	}

	public String read(String path) throws IOException {
		return new String(Files.readAllBytes(getFile(path)), UTF_8);
	}
	
	public String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), UTF_8);
	}

	private Path getFile(String relativePath) throws IOException {
		File file = new File(baseDirectory, relativePath);
		File parentDirectory = file.getParentFile();
		if (!parentDirectory.isDirectory()) {
			if (!parentDirectory.mkdirs()) {
				//Check if another thread managed to make the parent directory while we were checking
				if (!parentDirectory.isDirectory()) {
					throw new IOException("Could not create directory " + parentDirectory.getAbsolutePath());
				}
			}
		}
		return file.toPath();
	}

	public void delete(String relativePath) {
		final File file = new File(baseDirectory, relativePath);
		if (file.isFile() && !file.delete()) {
			logger.warn("Failed to delete file {}", file.getAbsolutePath());
		}
	}

	public void moveFiles(String fromRelativePath, String toRelativePath) throws IOException {
		File fromDir = new File(baseDirectory, fromRelativePath);
		File toDir = new File(baseDirectory, toRelativePath);
		FileUtils.moveDirectory(fromDir, toDir);
		
	}
}
