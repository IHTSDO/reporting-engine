package org.ihtsdo.termserver.scripting;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AncestorsCache implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(AncestorsCache.class);

	private static AncestorsCache singleton = null;
	private static AncestorsCache singletonStated = null;
	
	Map<Concept, Set<Concept>> ancestorsCache = new HashMap<>();
	CharacteristicType charType = CharacteristicType.INFERRED_RELATIONSHIP;
	
	public static AncestorsCache getAncestorsCache() {
		if (singleton == null) {
			singleton = new AncestorsCache();
		}
		return singleton;
	}
	
	public static AncestorsCache getStatedAncestorsCache() {
		if (singletonStated == null) {
			singletonStated = new AncestorsCache();
			singletonStated.charType = CharacteristicType.STATED_RELATIONSHIP;
		}
		return singletonStated;
	}
	
	private AncestorsCache() {
		//Force use of singleton;
	}
	
	public Set<Concept> getAncestors (Concept c) throws TermServerScriptException {
		return getAncestors(c, false);
	}
	
	public Set<Concept> getAncestorsSafely (Concept c) {
		try {
			return getAncestors(c, false);
		} catch (TermServerScriptException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public Set<Concept> getAncestors (Concept c, boolean mutable) throws TermServerScriptException {
		Set<Concept> ancestors = ancestorsCache.get(c);
		if (ancestors == null) {
			//Ensure we're working with the local copy rather than TS JSON
			Concept localConcept = GraphLoader.getGraphLoader().getConcept(c.getConceptId());
			ancestors = localConcept.getAncestors(NOT_SET, charType, false);
			ancestorsCache.put(localConcept, ancestors);
		}
		return mutable ? ancestors : Collections.unmodifiableSet(ancestors);
	}
	
	public Set<Concept> getAncestorsOrSelf (Concept c) throws TermServerScriptException {
		Set<Concept> ancestors = getAncestors(c, false);
		Set<Concept> orSelf = Collections.singleton(c);
		return ImmutableSet.copyOf(Iterables.concat(ancestors, orSelf));
	}
	
	/**
	 * Actually we're just going to throw a runtime exception if something goes wrong,
	 * so we can use this call inside a collection stream - neatly.
	 * @param c
	 * @return
	 */
	public Set<Concept> getAncestorsOrSelfSafely (Concept c) {
		try {
			return getAncestorsOrSelf(c);
		} catch (TermServerScriptException e) {
			throw new IllegalArgumentException("Failed to calculate ancestors of " + c, e);
		}
	}

	public void reset() {
		ancestorsCache = new HashMap<>();
	}
}
