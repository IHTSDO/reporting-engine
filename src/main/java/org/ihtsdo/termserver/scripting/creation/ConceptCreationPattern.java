package org.ihtsdo.termserver.scripting.creation;

import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class ConceptCreationPattern {
	
	String semTag;
	String termPattern;
	Strategy strategyForY;
	Concept existingConcept;
	List<ConceptCreationPattern> childPatterns = new ArrayList<>();
	List<ConceptCreationPattern> parentPatterns= new ArrayList<>();
	
	private ConceptCreationPattern() {
		//Force use of builder pattern starting with defineConceptPattern;
	}
	public static ConceptCreationPattern define() {
		return new ConceptCreationPattern();
	}
	
	public ConceptCreationPattern withTerm(String termPattern) {
		this.termPattern = termPattern;
		return this;
	}
	
	public ConceptCreationPattern withSemTag(String semTag) {
		this.semTag = semTag;
		return this;
	}
	
	public ConceptCreationPattern withStrategy(Strategy strategy) {
		this.strategyForY = strategy;
		return this;
	}
	
	public ConceptCreationPattern withExistingConcept(Concept existingConcept) {
		this.existingConcept = existingConcept;
		return this;
	}

	public ConceptCreationPattern addChildPattern (ConceptCreationPattern child) {
		childPatterns.add(child);
		return this;
	}
	
	public ConceptCreationPattern addParentPattern (ConceptCreationPattern parent) {
		parentPatterns.add(parent);
		return this;
	}
	
	Concept createPrototype (Concept X) throws TermServerScriptException {
		Concept concept = SnomedUtils.createConcept(generateTerm(X), semTag, null);
		for (ConceptCreationPattern parentPattern : parentPatterns) {
			Concept parent = parentPattern.createPrototype(X);
			concept.addParent(CharacteristicType.STATED_RELATIONSHIP, parent);
		}
		
		for (ConceptCreationPattern childPattern : childPatterns) {
			Concept child = childPattern.createPrototype(X);
			concept.addChild(CharacteristicType.STATED_RELATIONSHIP, child);
		}
		return concept;
	}
	private String generateTerm(Concept x) throws TermServerScriptException {
		//Are we replacing X or Y?
		String xTerm = x.getPreferredSynonym();
		String term = termPattern.replace("[X]", xTerm);
		if (strategyForY != null) {
			String yTerm = getY(x).getPreferredSynonym();
			term = termPattern.replace("[Y]", yTerm);
		}
		return term;
	}
	private Concept getY(Concept x) throws TermServerScriptException {
		if (strategyForY.equals(Strategy.ImmediateParentOfX)) {
			List<Concept> parents = x.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
			if (parents.size() == 1) {
				return parents.get(0);
			} else {
				throw new TermServerScriptException(x + " has multiple parents.  Can't determine Y");
			}
		} else {
			throw new IllegalStateException("Don't know how to deal with strategy " + strategyForY);
		}
	}
}
