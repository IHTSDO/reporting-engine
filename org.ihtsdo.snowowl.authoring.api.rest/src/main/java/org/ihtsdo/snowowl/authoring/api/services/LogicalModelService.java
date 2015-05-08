package org.ihtsdo.snowowl.authoring.api.services;

import org.ihtsdo.snowowl.authoring.api.model.logical.AttributeRestriction;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.terminology.Domain;
import org.ihtsdo.snowowl.authoring.api.terminology.DomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogicalModelService {

	@Autowired
	private DomainService domainService;

	@Autowired
	private ModelDAO modelDAO;

	public void saveLogicalModel(LogicalModel logicalModel) throws IOException {
		String name = logicalModel.getName();
		Assert.notNull(name, "Logical model name can not be null.");
		modelDAO.writeModel(logicalModel);
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

	public LogicalModel loadLogicalModelOrThrow(String name) throws IOException {
		LogicalModel logicalModel = loadLogicalModel(name);
		if (logicalModel == null) throw new SomethingNotFoundException(LogicalModel.class.getSimpleName(), name);
		return logicalModel;
	}

	public LogicalModel loadLogicalModel(String name) throws IOException {
		Assert.notNull(name, "Logical model name can not be null.");
		return modelDAO.loadModel(LogicalModel.class, name);
	}

	public List<String> listLogicalModelNames() {
		return modelDAO.listModelNames(LogicalModel.class);
	}

}
