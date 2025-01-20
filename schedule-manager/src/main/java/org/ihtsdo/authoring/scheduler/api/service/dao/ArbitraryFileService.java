package org.ihtsdo.authoring.scheduler.api.service.dao;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArbitraryFileService {

	protected File baseDirectory;

	public ArbitraryFileService() {
		this.baseDirectory = new File("resources/org.ihtsdo.authoring.single.api");
	}

	public void write(String path, String data) throws IOException {
		Files.writeString(getFile(path), data);
	}

	public String read(String path) throws IOException {
		return Files.readString(getFile(path));
	}
	
	public String read(File file) throws IOException {
		return Files.readString(file.toPath());
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

}
