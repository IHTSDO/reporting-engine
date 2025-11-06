package org.ihtsdo.termserver.scripting;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;


public class TransitiveClosure implements ScriptConstants {

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
		long id = Long.parseLong(c.getConceptId());
		if (!ancestorMap.containsKey(id)) {
			return new HashSet<>();
		}
		return ancestorMap.get(id);
	}
	
	public Set<Long> getDescendants (Concept c) {
		return getDescendants(c, null);
	}
	
	public Set<Long> getDescendants (Concept c, Predicate<Long> filter) {
		long id = Long.parseLong(c.getConceptId());
		if (!descendantMap.containsKey(id)) {
			return new HashSet<>();
		}
		
		if (filter == null) {
			return descendantMap.get(id);
		}
		return descendantMap.get(id).stream().filter(filter).collect(Collectors.toSet());
	}
	
	public boolean contains(Long id) {
		return descendantMap.containsKey(id);
	}
}
