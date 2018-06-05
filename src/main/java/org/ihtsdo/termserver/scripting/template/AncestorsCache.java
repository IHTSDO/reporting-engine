package org.ihtsdo.termserver.scripting.template;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class AncestorsCache implements RF2Constants {

	Map<Concept, Set<Concept>> ancestorsCache = new HashMap<>();
	
	public Set<Concept> getAncestors (Concept c) throws TermServerScriptException {
		return getAncestors(c, false);
	}
	
	private Set<Concept> getAncestors (Concept c, boolean mutable) throws TermServerScriptException {
		Set<Concept> ancestors = ancestorsCache.get(c);
		if (ancestors == null) {
			//Ensure we're working with the local copy rather than TS JSON
			Concept localConcept = GraphLoader.getGraphLoader().getConcept(c.getConceptId());
			ancestors = localConcept.getAncestors(NOT_SET);
			ancestorsCache.put(localConcept, ancestors);
		}
		return mutable ? ancestors : Collections.unmodifiableSet(ancestors);
	}
	
	public Set<Concept> getAncestorsOrSelf (Concept c) throws TermServerScriptException {
		Set<Concept> ancestors = getAncestors(c, true);
		ancestors.add(c);
		return Collections.unmodifiableSet(ancestors);
	}
	
	/**
	 * Actually we're just going to throw a runtime exception if something goes wrong,
	 * so we can use this call inside a collection stream - neatly.
	 * @param c
	 * @return
	 */
	public Set<Concept> getAncestorsOrSelfSafely (Concept c) {
		try {
			Set<Concept> ancestors = getAncestors(c, true);
			ancestors.add(c);
			return Collections.unmodifiableSet(ancestors);
		} catch (TermServerScriptException e) {
			throw new IllegalArgumentException("Failed to calculate ancestors of " + c, e);
		}
	}
}
