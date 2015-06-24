package org.ihtsdo.snowowl.authoring.single.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.snowowl.authoring.single.api.model.Template;
import org.ihtsdo.snowowl.authoring.single.api.model.work.WorkingContent;

import java.io.IOException;
import java.util.*;


public class TestContentServiceImpl implements ContentService {

	private Map<String, Set<String>> conceptDescendants;

	public TestContentServiceImpl() {
		conceptDescendants = new HashMap<>();
	}

	@Override
	public Set<String> getDescendantIds(IComponentRef ref) {
		return conceptDescendants.get(ref.getComponentId());
	}

	@Override
	public void createConcepts(Template template, WorkingContent content, String taskId) throws IOException {
		throw new NotImplementedException();
	}

	public void putDescendantIds(String concept, String[] descendantIds) {
		Set<String> descendantIdsSet = new HashSet<>();
		Collections.addAll(descendantIdsSet, descendantIds);
		conceptDescendants.put(concept, descendantIdsSet);
	}
}
