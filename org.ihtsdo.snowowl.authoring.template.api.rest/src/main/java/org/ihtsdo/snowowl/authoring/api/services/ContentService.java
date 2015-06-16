package org.ihtsdo.snowowl.authoring.single.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import org.ihtsdo.snowowl.authoring.single.api.model.Template;
import org.ihtsdo.snowowl.authoring.single.api.model.work.WorkingContent;

import java.io.IOException;
import java.util.Set;

public interface ContentService {

	Set<String> getDescendantIds(IComponentRef ref);

	void createConcepts(Template template, WorkingContent content, String taskId) throws IOException;

}
