package org.ihtsdo.termserver.scripting.creation;

import java.util.*;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.template.DescendentsCache;
	
public class ConceptCreator {
	
	ConceptCreationPattern conceptPattern;
	List<String> inspirations = new ArrayList<>();
	GraphLoader gl = GraphLoader.getGraphLoader();
	DescendentsCache cache = DescendentsCache.getDescendentsCache();
	
	static final String SEMTAG_BODY = "(body structure)";
	
	public boolean takesInspiration(Set<Concept> proposedInspirations) {
		//Is the proposed inspriration a descendant or self of the inspiriation this creator needs?
		try {
			nextInspiration:
			for (String inspirationStr : inspirations) {
				Concept inspiration = gl.getConcept(inspirationStr);
				//Do we have a match for this inspiration?
				Set<Concept> inspirationDescendantsSelf = cache.getDescendentsOrSelf(inspiration);
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


	public Set<Concept> createConceptSet(Set<Concept> inspiration) {
		// TODO Auto-generated method stub
		return null;
	}

}
