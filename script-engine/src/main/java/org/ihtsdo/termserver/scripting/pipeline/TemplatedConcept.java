package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import javax.swing.text.AbstractDocument;
import java.util.*;
import java.util.stream.Collectors;

public abstract class TemplatedConcept implements ScriptConstants, ConceptWrapper {

	protected TemplatedConcept(String externalIdentifier) {
		this.externalIdentifier = externalIdentifier;
	}

	public enum IterationIndicator { NEW, REMOVED, RESURRECTED, MODIFIED, UNCHANGED }

	protected static ContentPipelineManager cpm;
	protected static GraphLoader gl;

	protected static Set<String> partNumsMapped = new HashSet<>();
	protected static Set<String> partNumsUnmapped = new HashSet<>();
	
	protected static int mapped = 0;
	protected static int unmapped = 0;
	protected static int skipped = 0;
	protected static int conceptsModelled = 0;

	protected Concept existingConcept = null;
	protected boolean existingConceptHasInactivations = false;

	protected List<String> differencesFromExistingConcept = new ArrayList<>();

	protected IterationIndicator iterationIndicator;

	protected String externalIdentifier;

	protected List<String> issues;

	public static void reportStats(int tabIdx) throws TermServerScriptException {
		cpm.report(tabIdx, "");
		cpm.report(tabIdx, "Parts mapped", mapped);
		cpm.report(tabIdx, "Parts unmapped", unmapped);
		cpm.report(tabIdx, "Parts skipped", skipped);
		cpm.report(tabIdx, "Unique PartNums mapped", partNumsMapped.size());
		cpm.report(tabIdx, "Unique PartNums unmapped", partNumsUnmapped.size());
	}

	public String getExternalIdentifier() {
		return externalIdentifier;
	}

	public void setExternalIdentifier(String externalIdentifier) {
		this.externalIdentifier = externalIdentifier;
	}

	abstract public boolean isHighUsage();

	abstract public boolean isHighestUsage();

	public void addIssue(String issue) {
		if (issues == null) {
			issues = new ArrayList<>();
		}
		issues.add(issue);
	}

	public String getIssues() {
		if (issues == null) {
			return "";
		}
		return String.join("\n", issues);
	}

	public IterationIndicator getIterationIndicator() {
		return iterationIndicator;
	}

	public void setIterationIndicator(IterationIndicator iterationIndicator) {
		this.iterationIndicator = iterationIndicator;
	}


	public Concept getExistingConcept() {
		return existingConcept;
	}

	public void setExistingConcept(Concept existingConcept) {
		this.existingConcept = existingConcept;
	}

	public String getDifferencesFromExistingConcept() {
		return differencesFromExistingConcept.stream().collect(Collectors.joining(",\n"));
	}

	public void addDifferenceFromExistingConcept(String differenceFromExistingConcept) {
		this.differencesFromExistingConcept.add(differenceFromExistingConcept);
	}

	public boolean existingConceptHasInactivations() {
		return existingConceptHasInactivations;
	}

	public void setExistingConceptHasInactivations(boolean existingConceptHasInactivations) {
		this.existingConceptHasInactivations = existingConceptHasInactivations;
	}

}
