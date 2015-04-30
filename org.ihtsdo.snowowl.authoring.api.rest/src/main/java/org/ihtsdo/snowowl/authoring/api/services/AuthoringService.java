package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModelContentValidator;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModelContentValidator;
import org.ihtsdo.snowowl.authoring.api.model.work.ContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingContent;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileNotFoundException;
import java.io.IOException;
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

	@Autowired
	private WorkingContentService workingContentService;

	public ContentValidationResult validateWorkingContent(String templateName, String workId) throws IOException {
		Template template = getTemplate(templateName);
		if (template != null) {
			WorkingContent content = workingContentService.load(template, workId);
			if (content != null) {
				LogicalModel logicalModel = getLogicalModel(template.getLogicalModelName());
				LexicalModel lexicalModel = getLexicalModel(template.getLexicalModelName());
				ContentValidationResult result = new ContentValidationResult();
				logicalModelContentValidator.validate(content, logicalModel, result);
				lexicalModelContentValidator.validate(content, lexicalModel, result);
				return result;
			} else {
				throw new FileNotFoundException("Working content '" + workId + "' not found under template '" + templateName + "'.");
			}
		} else {
			throw new FileNotFoundException("Template '" + templateName + "' not found.");
		}
	}

	/**
	 * This persists the working content but does not create any concepts within the terminology server
	 * @param templateName
	 * @param content
	 * @return
	 */
	public void persistWork(String templateName, WorkingContent content) throws IOException {
		Template template = getTemplate(templateName);
		if (template != null) {
			workingContentService.saveOrUpdate(template, content);
		}
	}

	public WorkingContent loadWork(String templateName, String workId) throws IOException {
		Template template = getTemplate(templateName);
		return workingContentService.load(template, workId);
	}

	public Set<String> getDescendantIds(final IComponentRef ref) {
		return contentService.getDescendantIds(ref);
	}

	private Template getTemplate(String templateName) throws IOException {
		return templateService.loadTemplate(templateName);
	}

	private LogicalModel getLogicalModel(String logicalModelName) throws IOException {
		return logicalModelService.loadLogicalModel(logicalModelName);
	}

	private LexicalModel getLexicalModel(String lexicalModelName) throws IOException {
		return lexicalModelService.loadModel(lexicalModelName);
	}
}
