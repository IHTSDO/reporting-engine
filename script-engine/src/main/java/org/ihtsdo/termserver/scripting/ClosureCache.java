package org.ihtsdo.termserver.scripting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClosureCache implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ClosureCache.class);

	static ClosureCache singleton = null;
	private GraphLoader gl = null;
	private Map<Concept, Set<Concept>> closureCache = null;
	
	public static ClosureCache getClosureCache() {
		if (singleton == null) {
			singleton = new ClosureCache();
			singleton.closureCache = new HashMap<Concept, Set<Concept>>();
			singleton.gl = GraphLoader.getGraphLoader();
		}
		return singleton;
	}
	
	public Set<Concept> getClosure(Concept c) throws TermServerScriptException {
		if (!closureCache.containsKey(c)) {
			Concept preLoadedConcept = gl.getConcept(c.getConceptId());
			Set<Concept> descendants = preLoadedConcept.getDescendants(NOT_SET);
			closureCache.put(c, descendants);
		}
		return closureCache.get(c);
	}
	
}
