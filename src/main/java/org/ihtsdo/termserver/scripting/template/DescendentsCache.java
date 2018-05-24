package org.ihtsdo.termserver.scripting.template;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class DescendentsCache implements RF2Constants {

	Map<Concept, Set<Concept>> descendentOrSelfCache = new HashMap<>();
	
	public Set<Concept> getDescendentsOrSelf (Concept c) throws TermServerScriptException {
		if (c == null) {
			throw new IllegalArgumentException("Null concept requested");
		}
		//Ensure we're working with the local copy rather than TS JSON
		Concept localConcept = GraphLoader.getGraphLoader().getConcept(c.getConceptId());
		Set<Concept> descendents = descendentOrSelfCache.get(localConcept);
		if (descendents == null) {
			descendents = localConcept.getDescendents(NOT_SET);
			descendentOrSelfCache.put(localConcept, descendents);
		}
		descendents.add(localConcept); //Or Self
		return descendents;
	}

	public Set<Concept>  getDescendents(Concept c) throws TermServerScriptException {
		Set<Concept> descendents = getDescendentsOrSelf(c);
		descendents.remove(c);  // Not self!
		return descendents;
	}
	
	public Set<Concept> getDescendentsOrSelf (String sctid) throws TermServerScriptException {
		Concept c = GraphLoader.getGraphLoader().getConcept(sctid);
		return getDescendentsOrSelf(c);
	}
}
