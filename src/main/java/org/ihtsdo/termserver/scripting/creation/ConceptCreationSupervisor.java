package org.ihtsdo.termserver.scripting.creation;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class ConceptCreationSupervisor implements RF2Constants {

	private static ConceptCreationSupervisor singleton = null;
	List<ConceptCreator> creators = new ArrayList<>();
	
	public static ConceptCreationSupervisor getSupervisor() {
		if (singleton == null) {
			singleton = new ConceptCreationSupervisor();
		}
		return singleton;
	}
	
	public void registerCreator (ConceptCreator creator) {
		creators.add(creator);
	}
	
	Concept createConcept (Set<Concept> inspiration) throws TermServerScriptException {
		//Do any creators take this inspiration?
		List<ConceptCreator> matches = creators.stream().filter(c -> c.takesInspiration(inspiration)).collect(Collectors.toList());
		if (matches.size() == 0) {
			throw new TermServerScriptException("Don't know how to make a concept from " + inspiration.stream().map(c -> c.toString()).collect(Collectors.joining (",\n  ")));
		} else if (matches.size() > 1) {
			throw new TermServerScriptException("Multiple creators matched " + inspiration.stream().map(c -> c.toString()).collect(Collectors.joining (",\n  ")));
		} else {
			return matches.get(0).createConcept(inspiration);
		}
	}

}
