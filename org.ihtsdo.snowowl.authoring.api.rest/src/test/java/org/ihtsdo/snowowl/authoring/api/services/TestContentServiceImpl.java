package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;

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

	public void putDescendantIds(String concept, String[] descendantIds) {
		Set<String> descendantIdsSet = new HashSet<>();
		Collections.addAll(descendantIdsSet, descendantIds);
		conceptDescendants.put(concept, descendantIdsSet);
	}
}
