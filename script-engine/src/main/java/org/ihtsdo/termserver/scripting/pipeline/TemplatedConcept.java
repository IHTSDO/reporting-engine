package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.util.*;

public abstract class TemplatedConcept implements ScriptConstants, ConceptWrapper {

	protected static ContentPipelineManager cpm;
	protected static GraphLoader gl;

	protected static Set<String> partNumsMapped = new HashSet<>();
	protected static Set<String> partNumsUnmapped = new HashSet<>();
	
	protected static int mapped = 0;
	protected static int unmapped = 0;
	protected static int skipped = 0;
	protected static int conceptsModelled = 0;

	protected String externalIdentifier;

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
}
