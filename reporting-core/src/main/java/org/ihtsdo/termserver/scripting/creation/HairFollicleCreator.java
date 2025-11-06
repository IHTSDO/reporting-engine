package org.ihtsdo.termserver.scripting.creation;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;

/**
 * We will need about 50 new Structure concepts for hair follicle. 
 * Please see the attached disorder concepts that have been modelled by hair follicle structure. 
 * All new structure concepts should have Entire concepts as their subtype. It means this task 
 * will have 100 new anatomy concepts. I would recommend this task to be completed before any 
 * changes to disorders. I am happy to review the task to ensure they are correctly modeled. 
 * 
 * The following is the IS A modeling pattern. A new structure should have at least two parent
 *  concepts, one Entire concept, and/or other new subconcepts hair follicle.
 *  
 *  Skin structure of X (body structure)
 *  Structure of hair follicle of Y (body structure)  -  Y is the direct super concept region of X, e.g. upper limb structure is super concept of upper arm.
 *   Structure of hair follicle of X (body structure)
 *      Entire hair follicle of X (body structure)

For your example |Structure of hair follicle of upper arm (body structure)|:

371309009 |Skin structure of upper arm (body structure)|
xxxxxx |Structure of hair follicle of upper limb (body structure)|
  xxxxxx |Entire hair follicle of upper limb (body structure)|
  xxxxxxx |Structure of hair follicle of upper arm (body structure)|   <-- Target Concept
    xxxxxx |Entire hair follicle of upper arm (body structure)|
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HairFollicleCreator extends ConceptCreator {

	private static final Logger LOGGER = LoggerFactory.getLogger(HairFollicleCreator.class);

	private static HairFollicleCreator singleton = null;
	public static HairFollicleCreator getHairFollicleCreator() {
		if (singleton == null) {
			singleton = new HairFollicleCreator();
			singleton.addInspiration("67290009 |Hair follicle structure (body structure)|");
			singleton.addInspiration("127856007 |Skin and/or subcutaneous tissue structure (body structure)|");
			
			ConceptCreationPattern pattern = ConceptCreationPattern
					.define()
					.withTerm("Structure of hair follicle of [X]")
					.withSemTag(SEMTAG_BODY);
			
			ConceptCreationPattern parent1 = ConceptCreationPattern
					.define()
					.withTerm("Structure of hair follicle of [Y]")
					.withSemTag(SEMTAG_BODY)
					.withStrategy(Strategy.ImmediateParentOfX);
			
			ConceptCreationPattern parent2 = ConceptCreationPattern
					.define()
					.withTerm("Skin structure of [X]")
					.withSemTag(SEMTAG_BODY);
			
			ConceptCreationPattern sibling = ConceptCreationPattern
					.define()
					.withTerm("Entire hair follicle of [Y]")
					.withSemTag(SEMTAG_BODY)
					.withStrategy(Strategy.ImmediateParentOfX);
			
			ConceptCreationPattern child = ConceptCreationPattern
					.define()
					.withTerm("Entire hair follicle of [X]")
					.withSemTag(SEMTAG_BODY);
			
			pattern.addParentPattern(parent1)
				.addParentPattern(parent2)
				.addSiblingPattern(sibling)
				.addChildPattern(child);
			
			singleton.setConceptPattern(pattern);
		}
		return singleton;
	}


	@Override
	public Concept createConcept(Set<Concept> inspiration) throws TermServerScriptException {
		Concept hfs = gl.getConcept("67290009 |Hair follicle structure (body structure)");
		//Now our real driver is coming from whatever structure is part of the remaining concept
		inspiration.remove(hfs);
		Concept conceptOfInterest = inspiration.iterator().next();
		String structure = conceptOfInterest.getPreferredSynonym().split(" of ")[1].toLowerCase();
		
		//We're looking for some sort of structure
		if (!structure.contains(STRUCTURE)) {
			structure += " " + STRUCTURE;
		}
		
		//Now that's our X, if we can find it
		Concept X = findAnatomy(structure);
		if (X == null) {
			throw new TermServerScriptException("Unable to find structure concept for " + conceptOfInterest);
		} 
		return conceptPattern.createPrototype(X);
	}

}
