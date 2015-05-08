package org.ihtsdo.snowowl.authoring.api.services;

import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;

public class TemplateService {

	@Autowired
	private ModelDAO modelDAO;

	@Autowired
	private LogicalModelService logicalModelService;

	@Autowired
	private LexicalModelService lexicalModelService;

	public String saveTemplate(Template template) throws IOException {
		String errorMessage = validate(template);
		if (errorMessage != null) {
			return errorMessage;
		} else {
			modelDAO.writeModel(template);
			return null;
		}
	}

	public String validate(Template template) throws IOException {
		if (logicalModelService.loadLogicalModel(template.getLogicalModelName()) == null) {
			return "Named logical model not found.";
		} else if (lexicalModelService.loadModel(template.getLexicalModelName()) == null) {
			return "Named lexical model not found.";
		} else {
			String templateName = template.getName();
			if (templateName == null || templateName.isEmpty()) {
				return "Template name is required.";
			}
		}
		return null;
	}

	public Template loadTemplateOrThrow(String templateName) throws IOException {
		Template template = loadTemplate(templateName);
		if (template == null) throw new SomethingNotFoundException(Template.class.getSimpleName(), templateName);
		return template;
	}

	public Template loadTemplate(String name) throws IOException {
		Assert.notNull(name, "Template name can not be null.");
		return modelDAO.loadModel(Template.class, name);
	}

	public List<String> listModelNames() {
		return modelDAO.listModelNames(Template.class);
	}
}
