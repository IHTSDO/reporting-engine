package org.ihtsdo.termserver.scripting;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DescendantsCache implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DescendantsCache.class);

	private static DescendantsCache singleton = null;
	private static DescendantsCache singletonStated = null;
	
	Map<Concept, Set<Concept>> descendantCache = new HashMap<>();
	CharacteristicType charType = CharacteristicType.INFERRED_RELATIONSHIP;
	
	public static DescendantsCache getDescendantsCache() {
		if (singleton == null) {
			singleton = new DescendantsCache();
		}
		return singleton;
	}
	
	public static DescendantsCache getStatedDescendantsCache() {
		if (singletonStated == null) {
			singletonStated = new DescendantsCache();
		}
		singletonStated.charType = CharacteristicType.STATED_RELATIONSHIP;
		return singletonStated;
	}
	
	private DescendantsCache() {
		//Force use of singleton;
	}
	
	public void reset() {
		descendantCache = new HashMap<>();
	}
	
	public Set<Concept> getDescendants(Concept c) throws TermServerScriptException {
		return getDescendants(c, false);  //Default implementation is immutable
	}
	
	public Set<Concept> getDescendants(Concept c, boolean mutable) throws TermServerScriptException {
		if (c == null) {
			throw new IllegalArgumentException("Null concept requested");
		}

		//Ensure we're working with the local copy rather than TS JSON
		Concept localConcept = GraphLoader.getGraphLoader().getConcept(c.getConceptId());
		if (localConcept == null || localConcept.isActive() == null) {
			throw new TermServerScriptException("Attempt to obtain descendants of non-existent concept: " + c.getConceptId());
		}
		
		if (!localConcept.isActive()) {
			throw new TermServerScriptException(c + " is inactive. Unlikely you want to find its decendants");
		}
		Set<Concept> descendants = descendantCache.get(localConcept);
		if (descendants == null) {
			descendants = localConcept.getDescendants(NOT_SET);
			//Don't allow anyone to change this!
			descendantCache.put(localConcept, descendants);
		}
		return mutable ? new HashSet<>(descendants) : Collections.unmodifiableSet(descendants);
	}

	public Set<Concept> getDescendantsOrSelf(Concept c) throws TermServerScriptException {
		Set<Concept> descendants = getDescendants(c, false);
		Set<Concept> orSelf = Collections.singleton(c);
		return ImmutableSet.copyOf(Iterables.concat(descendants, orSelf));
	}
	
	public Set<Concept> getDescendantsOrSelf(Concept c, boolean mutable) throws TermServerScriptException {
		Set<Concept> dOrS = getDescendantsOrSelf(c);
		return mutable ? new HashSet<>(dOrS) : Collections.unmodifiableSet(dOrS);
	}

	public Set<Concept> getDescendantsOrSelf(String sctid) throws TermServerScriptException {
		Concept c = GraphLoader.getGraphLoader().getConcept(sctid);
		return getDescendantsOrSelf(c);
	}
}
