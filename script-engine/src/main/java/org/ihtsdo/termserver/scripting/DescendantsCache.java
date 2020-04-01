package org.ihtsdo.termserver.scripting;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DescendantsCache implements RF2Constants {

	private static DescendantsCache singleton = null;
	
	Map<Concept, Set<Concept>> descendentCache = new HashMap<>();
	
	public static DescendantsCache getDescendentsCache() {
		if (singleton == null) {
			singleton = new DescendantsCache();
		}
		return singleton;
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
