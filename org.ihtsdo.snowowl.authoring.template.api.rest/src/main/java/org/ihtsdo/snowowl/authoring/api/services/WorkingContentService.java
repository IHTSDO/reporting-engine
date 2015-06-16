package org.ihtsdo.snowowl.authoring.single.api.services;

import org.ihtsdo.snowowl.authoring.single.api.model.Template;
import org.ihtsdo.snowowl.authoring.single.api.model.work.WorkingContent;
import org.ihtsdo.snowowl.authoring.single.api.util.RandomShaGenerator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

public class WorkingContentService {

	@Autowired
	private ModelDAO modelDAO;

	@Autowired
	private RandomShaGenerator randomShaGenerator;

	public void saveOrUpdate(Template template, WorkingContent content) throws IOException {
		if (content.getName() == null) {
			String sha;
			do {
				sha = randomShaGenerator.generateRandomSha();
			} while (load(template, sha) != null);
			content.setName(sha);
		}
		modelDAO.writeModel(template, content);
	}

	public WorkingContent loadOrThrow(Template template, String name) throws IOException {
		WorkingContent content = load(template, name);
		if (content == null) throw new SomethingNotFoundException(WorkingContent.class.getSimpleName(), name + ", part of template " + template.getName() + ",");
		return content;
	}

	public WorkingContent load(Template template, String name) throws IOException {
		return modelDAO.loadModel(template, WorkingContent.class, name);
	}

	public List<String> list(Template template) {
		return modelDAO.listModelNames(template, WorkingContent.class);
	}
}
