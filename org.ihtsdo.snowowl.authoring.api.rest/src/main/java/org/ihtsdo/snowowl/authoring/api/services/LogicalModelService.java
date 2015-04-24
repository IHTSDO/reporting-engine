package org.ihtsdo.snowowl.authoring.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.snowowl.authoring.api.model.logical.AttributeRestriction;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.terminology.Domain;
import org.ihtsdo.snowowl.authoring.api.terminology.DomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogicalModelService {

	public static final String JSON_EXTENSION = ".json";

	@Autowired
	private ObjectMapper jsonMapper;

	@Autowired
	private DomainService domainService;

	private File baseFilesDirectory;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public LogicalModelService() {
		this(new File(""));
	}

	public LogicalModelService(File baseFilesDirectory) {
		this.baseFilesDirectory = baseFilesDirectory;
	}


	public void saveLogicalModel(LogicalModel logicalModel) throws IOException {
		String name = logicalModel.getName();
		Assert.notNull(name, "Logical model name can not be null.");
		try (FileWriter writer = new FileWriter(getLogicalModelFile(name))) {
			jsonMapper.writeValue(writer, logicalModel);
		}
	}

	public List<String> validateLogicalModel(LogicalModel logicalModel) {
		List<String> messages = new ArrayList<>();
		String logicalModelDomainName = logicalModel.getDomainName();
		if (logicalModelDomainName != null) {
			Domain domain = domainService.findDomainByName(logicalModelDomainName);
			if (domain != null) {
				List<String> allowedAttributes = domain.getAllowedAttributes();
				Set<String> allAttributeRestrictionIds = new HashSet<>();
				for (List<AttributeRestriction> attributeRestrictions : logicalModel.getAttributeRestrictionGroups()) {
					for (AttributeRestriction attributeRestriction : attributeRestrictions) {
						allAttributeRestrictionIds.add(attributeRestriction.getTypeConceptId());
					}
				}
				allAttributeRestrictionIds.removeAll(allowedAttributes);
				for (String attributeRestrictionIdNotAllowed : allAttributeRestrictionIds) {
					messages.add("Attribute type '"+ attributeRestrictionIdNotAllowed + "' not allowed in this domain.");
				}
			} else {
				messages.add("Domain named '" + logicalModelDomainName + "' not found.");
			}
		} else {
			messages.add("Logical model domain is mandatory.");
		}
		return messages;
	}

	public LogicalModel loadLogicalModel(String name) throws IOException {
		Assert.notNull(name, "Logical model name can not be null.");
		File logicalModelFile = getLogicalModelFile(name);
		if (logicalModelFile.isFile()) {
			try (FileReader src = new FileReader(logicalModelFile)) {
				return jsonMapper.readValue(src, LogicalModel.class);
			}
		} else {
			throw new LogicalModelNotFoundException(name);
		}
	}

	public List<String> listLogicalModelNames() {
		File logicalModelsDirectory = getLogicalModelsDirectory();
		File[] files = logicalModelsDirectory.listFiles();

		logger.info("logicalModelsDirectory {}", logicalModelsDirectory);
		logger.info("files {}", (Object[])files);

		List<String> names = new ArrayList<>();
		for (File file : files) {
			String name = file.getName();
			logger.info("Found file {}", name);
			if (name.endsWith(JSON_EXTENSION)) {
				names.add(name.replaceFirst("\\.json$", ""));
			}
		}
		logger.info("Names {}", names);
		return names;
	}

	private File getLogicalModelFile(String name) {
		File logicalModelsDirectory = getLogicalModelsDirectory();
		return new File(logicalModelsDirectory, name + JSON_EXTENSION);
	}

	private File getLogicalModelsDirectory() {
		File logicalModelsDirectory = new File(baseFilesDirectory, "resources/org.ihtsdo.snowowl.authoring/logical-models");
		if (!logicalModelsDirectory.isDirectory()) {
			logicalModelsDirectory.mkdirs();
		}
		return logicalModelsDirectory;
	}

	public void setBaseFilesDirectory(File baseFilesDirectory) {
		this.baseFilesDirectory = baseFilesDirectory;
	}

}
