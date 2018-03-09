package org.ihtsdo.termserver.scripting.template;

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
		Set<Concept> ancestors = ancestorsCache.get(c);
		if (ancestors == null) {
			//Ensure we're working with the local copy rather than TS JSON
			Concept localConcept = GraphLoader.getGraphLoader().getConcept(c.getConceptId());
			ancestors = localConcept.getAncestors(NOT_SET);
			ancestorsCache.put(localConcept, ancestors);
		}
		return ancestors;
	}
}
