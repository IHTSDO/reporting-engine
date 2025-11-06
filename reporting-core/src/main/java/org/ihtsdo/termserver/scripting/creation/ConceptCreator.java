package org.ihtsdo.termserver.scripting.creation;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
	

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConceptCreator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptCreator.class);

	ConceptCreationPattern conceptPattern;
	List<String> inspirations = new ArrayList<>();
	GraphLoader gl = GraphLoader.getGraphLoader();
	DescendantsCache cache = DescendantsCache.getDescendantsCache();
	private Map<String, Concept> anatomyMap = null;
	
	static final String SEMTAG_BODY = "(body structure)";
	static final String STRUCTURE = "structure";
	
	public boolean takesInspiration(Set<Concept> proposedInspirations) {
		//Is the proposed inspriration a descendant or self of the inspiriation this creator needs?
		try {
			nextInspiration:
			for (String inspirationStr : inspirations) {
				Concept inspiration = gl.getConcept(inspirationStr);
				//Do we have a match for this inspiration?
				Set<Concept> inspirationDescendantsSelf = cache.getDescendantsOrSelf(inspiration);
				for (Concept thisProposed : proposedInspirations) {
					if (inspirationDescendantsSelf.contains(thisProposed)) {
						continue nextInspiration;
					}
				}
				return false;
			}
			return true;
		} catch (Exception e) {
			throw new IllegalArgumentException("Unexpected data condition when seeking inspiration",e);
		}
	}

	protected void addInspiration(String inspiration) {
		inspirations.add(inspiration);
	}
	
	abstract public Concept createConcept(Set<Concept> inspiration) throws TermServerScriptException ;

	public ConceptCreationPattern getConceptPattern() {
		return conceptPattern;
	}

	public void setConceptPattern(ConceptCreationPattern conceptPattern) {
		this.conceptPattern = conceptPattern;
	}
	

	protected Concept findAnatomy(String pt) throws TermServerScriptException {
		if (anatomyMap == null) {
			populateAnatomyMap();
		}
		return anatomyMap.get(pt.toLowerCase());
	}

	private void populateAnatomyMap() throws TermServerScriptException {
		anatomyMap = new HashMap<>();
		for (Concept c : BODY_STRUCTURE.getDescendants(NOT_SET)) {
			anatomyMap.put(c.getPreferredSynonym().toLowerCase(), c);
		}
	}
}
