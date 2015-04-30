package org.ihtsdo.snowowl.authoring.api.services;

import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingContent;
import org.ihtsdo.snowowl.authoring.api.util.RandomShaGenerator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

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

	public WorkingContent load(Template template, String sha) throws IOException {
		return modelDAO.loadModel(template, WorkingContent.class, sha);
	}

}
