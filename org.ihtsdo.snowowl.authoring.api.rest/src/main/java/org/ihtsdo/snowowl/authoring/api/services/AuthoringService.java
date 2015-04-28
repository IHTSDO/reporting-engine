package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModelContentValidator;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModelContentValidator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AuthoringService {

	@Autowired
	private LogicalModelContentValidator logicalModelContentValidator;

	@Autowired
	private LexicalModelContentValidator lexicalModelContentValidator;

	@Autowired
	private LogicalModelService logicalModelService;

	@Autowired
	private LexicalModelService lexicalModelService;

	@Autowired
	private TemplateService templateService;

	@Autowired
	private ContentService contentService;

	public List<AuthoringContentValidationResult> validateContent(String templateName, List<AuthoringContent> content) throws IOException {
		Template template = templateService.loadTemplate(templateName);
		if (template != null) {
			LogicalModel logicalModel = getLogicalModel(template.getLogicalModelName());
			LexicalModel lexicalModel = getLexicalModel(template.getLexicalModelName());
			List<AuthoringContentValidationResult> results = new ArrayList<>();
			for (AuthoringContent authoringContent : content) {
				results.add(validateContent(logicalModel, lexicalModel, authoringContent));
			}
			return results;
		} else {
			throw new FileNotFoundException("Template '" + templateName + "' not found.");
		}
	}

	private AuthoringContentValidationResult validateContent(LogicalModel logicalModel, LexicalModel lexicalModel, AuthoringContent content) throws IOException {
		AuthoringContentValidationResult result = logicalModelContentValidator.validate(content, logicalModel);
		lexicalModelContentValidator.validate(content, lexicalModel, result);
		return result;
	}

	public Set<String> getDescendantIds(final IComponentRef ref) {
		return contentService.getDescendantIds(ref);
	}

	private LogicalModel getLogicalModel(String logicalModelName) throws IOException {
		return logicalModelService.loadLogicalModel(logicalModelName);
	}

	private LexicalModel getLexicalModel(String lexicalModelName) throws IOException {
		return lexicalModelService.loadModel(lexicalModelName);
	}

}
