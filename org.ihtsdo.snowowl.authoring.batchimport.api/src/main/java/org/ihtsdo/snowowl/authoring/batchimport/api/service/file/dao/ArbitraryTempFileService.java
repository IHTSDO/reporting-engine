package org.ihtsdo.snowowl.authoring.batchimport.api.service.file.dao;

import org.slf4j.LoggerFactory;

import java.io.File;

public class ArbitraryTempFileService extends ArbitraryFileService {

	public ArbitraryTempFileService(String functionalArea) {
		logger = LoggerFactory.getLogger(ArbitraryTempFileService.class);
		this.baseDirectory = new File("work/tmp/" + functionalArea);
	}

	public File[] listFiles(String relativePath) {
		File file = new File(baseDirectory, relativePath);
		return file.listFiles();
	}

}
