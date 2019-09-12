package org.ihtsdo.termserver.scripting;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class TransitiveClosure implements RF2Constants {

	Map<Long, Set<Long>> ancestorMap = Collections.synchronizedMap(new HashMap<Long, Set<Long>>());
	Map<Long, Set<Long>> descendantMap = Collections.synchronizedMap(new HashMap<Long, Set<Long>>());
	
	public void addConcept(Concept c) throws TermServerScriptException {
		Long descendant = Long.parseLong(c.getConceptId());
		//Calculate and add all my ancestors
		Set<Long> ancestors = c.getAncestors(NOT_SET).stream()
				.map(a -> Long.parseLong(a.getConceptId()))
				.collect(Collectors.toSet());
		ancestorMap.put(descendant, ancestors);
		
		//But also, each of these ancestors can add me as a descendant
		for (Long ancestor : ancestors) {
			//Have we seen this ancestor before?
			Set<Long> descendants = descendantMap.get(ancestor);
			if (descendants == null) {
				descendants = Collections.synchronizedSet(new HashSet<Long>());
				descendantMap.put(ancestor, descendants);
			}
			descendants.add(descendant);
		}
	}
	
	public int size() {
		return descendantMap.values().stream()
				.mapToInt(s -> s.size())
				.sum();
	}
	
	public Set<Long> getAncestors (Concept c) {
		return ancestorMap.get(Long.parseLong(c.getConceptId()));
	}
	
	public Set<Long> getDescendants (Concept c) {
		return descendantMap.get(Long.parseLong(c.getConceptId()));
	}
}
