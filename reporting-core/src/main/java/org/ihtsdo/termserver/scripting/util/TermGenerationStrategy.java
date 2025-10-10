package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;

public interface TermGenerationStrategy {

	String suggestTerm(Concept concept, String termModifier);

	boolean applyTermViaOverride(Concept original, Concept clone, String termModifier) throws TermServerScriptException;
}

