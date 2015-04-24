package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModelContentValidator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AuthoringService {

	@Autowired
	private LogicalModelContentValidator logicalModelContentValidator;

	@Autowired
	private LogicalModelService logicalModelService;

	@Autowired
	private ContentService contentService;

	public List<AuthoringContentValidationResult> validateContent(String logicalModelName, List<AuthoringContent> content) throws IOException {
		List<AuthoringContentValidationResult> results = new ArrayList<>();
		for (AuthoringContent authoringContent : content) {
			results.add(validateContent(logicalModelName, authoringContent));
		}
		return results;
	}

	public AuthoringContentValidationResult validateContent(String logicalModelName, AuthoringContent content) throws IOException {
		LogicalModel logicalModel = logicalModelService.loadLogicalModel(logicalModelName);
		return logicalModelContentValidator.validate(content, logicalModel);
	}

	public Set<String> getDescendantIds(final IComponentRef ref) {
		return contentService.getDescendantIds(ref);
	}

}
