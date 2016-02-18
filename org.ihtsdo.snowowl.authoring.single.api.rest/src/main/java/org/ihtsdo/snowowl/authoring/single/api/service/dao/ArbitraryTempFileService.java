package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import org.slf4j.LoggerFactory;

import java.io.File;

public class ArbitraryTempFileService extends ArbitraryFileService{

	public ArbitraryTempFileService(String functionalArea) {
		logger = LoggerFactory.getLogger(ArbitraryTempFileService.class);
		this.baseDirectory = new File("work/tmp/" + functionalArea);
	}

}
