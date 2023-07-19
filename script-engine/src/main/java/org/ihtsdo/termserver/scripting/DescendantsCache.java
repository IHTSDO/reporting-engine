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

	private static Logger LOGGER = LoggerFactory.getLogger(DescendantsCache.class);

	private static DescendantsCache singleton = null;
	private static DescendantsCache singletonStated = null;
	
	Map<Concept, Set<Concept>> descendentCache = new HashMap<>();
	CharacteristicType charType = CharacteristicType.INFERRED_RELATIONSHIP;
	
	public static DescendantsCache getDescendentsCache() {
		if (singleton == null) {
			singleton = new DescendantsCache();
		}
		return singleton;
	}
	
	public static DescendantsCache getStatedDescendentsCache() {
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
		descendentCache = new HashMap<>();
	}
	
	public Set<Concept> getDescendents(Concept c) throws TermServerScriptException {
		return getDescendents(c, false);  //Default implementation is immutable
	}
	
	public Set<Concept> getDescendents (Concept c, boolean mutable) throws TermServerScriptException {
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
		Set<Concept> descendents = descendentCache.get(localConcept);
		if (descendents == null) {
			descendents = localConcept.getDescendents(NOT_SET);
			//Don't allow anyone to change this!
			descendentCache.put(localConcept, descendents);
		}
		return mutable ? new HashSet<>(descendents) : Collections.unmodifiableSet(descendents);
	}

	public Set<Concept> getDescendentsOrSelf(Concept c) throws TermServerScriptException {
		Set<Concept> descendents = getDescendents(c, false);
		Set<Concept> orSelf = Collections.singleton(c);
		return ImmutableSet.copyOf(Iterables.concat(descendents, orSelf));
	}
	
	public Set<Concept> getDescendentsOrSelf(Concept c, boolean mutable) throws TermServerScriptException {
		Set<Concept> dOrS = getDescendentsOrSelf(c);
		return mutable ? new HashSet<>(dOrS) : Collections.unmodifiableSet(dOrS);
	}

	public Set<Concept> getDescendentsOrSelf (String sctid) throws TermServerScriptException {
		Concept c = GraphLoader.getGraphLoader().getConcept(sctid);
		return getDescendentsOrSelf(c);
	}
}
