package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModelContentValidator;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModelContentValidator;
import org.ihtsdo.snowowl.authoring.api.model.work.ContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingContent;
import org.springframework.beans.factory.annotation.Autowired;

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

	@Autowired
	private JiraProjectService jiraProjectService;

	public ContentValidationResult validateWorkingContent(String templateName, String workId) throws IOException {
		Template template = templateService.loadTemplateOrThrow(templateName);
		WorkingContent content = workingContentService.loadOrThrow(template, workId);
		return validateWorkingContent(template, content);
	}

	private ContentValidationResult validateWorkingContent(Template template, WorkingContent content) throws IOException {
		LogicalModel logicalModel = getLogicalModel(template.getLogicalModelName());
		LexicalModel lexicalModel = getLexicalModel(template.getLexicalModelName());
		ContentValidationResult result = new ContentValidationResult();
		logicalModelContentValidator.validate(content, logicalModel, result);
		lexicalModelContentValidator.validate(content, lexicalModel, result);
		return result;
	}

	/**
	 * This persists the working content but does not create any concepts within the terminology server
	 * @param templateName
	 * @param content
	 * @return
	 */
	public void persistWork(String templateName, WorkingContent content) throws IOException {
		Template template = templateService.loadTemplateOrThrow(templateName);
		workingContentService.saveOrUpdate(template, content);
	}

	public WorkingContent commitWorkingContent(String templateName, String workId) throws IOException, JiraException {
		Template template = templateService.loadTemplateOrThrow(templateName);
		WorkingContent content = workingContentService.loadOrThrow(template, workId);
		String taskId = jiraProjectService.createJiraTask();
		content.setTaskId(taskId);
		contentService.createConcepts(template, content, taskId);
		workingContentService.saveOrUpdate(template, content);
		return content;
	}

	public Set<String> getDescendantIds(final IComponentRef ref) {
		return contentService.getDescendantIds(ref);
	}

	public WorkingContent loadWorkOrThrow(String templateName, String workId) throws IOException {
		Template template = templateService.loadTemplateOrThrow(templateName);
		return workingContentService.loadOrThrow(template, workId);
	}

	private LogicalModel getLogicalModel(String logicalModelName) throws IOException {
		return logicalModelService.loadLogicalModel(logicalModelName);
	}

	private LexicalModel getLexicalModel(String lexicalModelName) throws IOException {
		return lexicalModelService.loadModel(lexicalModelName);
	}
}
