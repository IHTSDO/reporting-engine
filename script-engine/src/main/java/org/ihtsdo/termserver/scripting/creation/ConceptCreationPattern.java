package org.ihtsdo.termserver.scripting.creation;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConceptCreationPattern implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptCreationPattern.class);

	String semTag;
	String termPattern;
	Strategy strategyForY;
	Concept existingConcept;
	List<ConceptCreationPattern> childPatterns = new ArrayList<>();
	List<ConceptCreationPattern> parentPatterns= new ArrayList<>();
	List<ConceptCreationPattern> siblingPatterns= new ArrayList<>();
	
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
	
	public ConceptCreationPattern addSiblingPattern (ConceptCreationPattern sibling) {
		siblingPatterns.add(sibling);
		return this;
	}
	
	Concept createPrototype (Concept X) throws TermServerScriptException {
		Concept concept = SnomedUtils.createConcept(generateTerm(X), semTag, null);
		List<Concept> siblings = createSiblings(X);
		for (ConceptCreationPattern parentPattern : parentPatterns) {
			Concept parent = parentPattern.createPrototype(X);
			parent.addChild(CharacteristicType.STATED_RELATIONSHIP, concept);
			concept.addParent(CharacteristicType.STATED_RELATIONSHIP, parent);
			for (Concept sibling : siblings) {
				sibling.addParent(CharacteristicType.STATED_RELATIONSHIP, parent);
				parent.addChild(CharacteristicType.STATED_RELATIONSHIP, sibling);
			}
		}
		
		for (ConceptCreationPattern childPattern : childPatterns) {
			Concept child = childPattern.createPrototype(X);
			concept.addChild(CharacteristicType.STATED_RELATIONSHIP, child);
		}
		return concept;
	}
	
	private List<Concept> createSiblings(Concept x) throws TermServerScriptException {
		List<Concept> siblings = new ArrayList<>();
		for (ConceptCreationPattern siblingPattern : siblingPatterns) {
			siblings.add(siblingPattern.createPrototype(x));
		}
		return siblings;
	}
	private String generateTerm(Concept x) throws TermServerScriptException {
		//Are we replacing X or Y?
		String xTerm = StringUtils.deCapitalize(x.getPreferredSynonym());  //TODO Check case significance before making lower case!
		String term = termPattern.replace("[X]", xTerm);
		if (strategyForY != null) {
			String yTerm = StringUtils.deCapitalize(getY(x).getPreferredSynonym()); //TODO Check case significance before making lower case!
			term = termPattern.replace("[Y]", yTerm);
		}
		return term;
	}
	private Concept getY(Concept x) throws TermServerScriptException {
		if (strategyForY.equals(Strategy.ImmediateParentOfX)) {
			Set<Concept> parents = x.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
			if (parents.size() == 0) {
				throw new TermServerScriptException(x + " has no inferred parents (?!).  Can't determine Y");
			} else if (parents.size() == 1) {
				return parents.iterator().next();
			} else {
				throw new TermServerScriptException(x + " has multiple parents.  Can't determine Y");
			}
		} else {
			throw new IllegalStateException("Don't know how to deal with strategy " + strategyForY);
		}
	}
}
