package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CaseSignificanceFixDriven extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSignificanceFixDriven.class);

	Map<Concept, List<Description>> conceptDescriptionsToProcess = new HashMap<>();

	Pattern descIdCompIdPattern;

	protected CaseSignificanceFixDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CaseSignificanceFixDriven fix = new CaseSignificanceFixDriven(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_UPDATES);
			fix.descIdCompIdPattern = Pattern.compile("(\\d+).*?\\[(\\d+)\\]");
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		for (Description d : conceptDescriptionsToProcess.get(loadedConcept)) {
			Description dLoaded = loadedConcept.getDescription(d.getDescriptionId());
			dLoaded.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			report(task, loadedConcept, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, dLoaded);
		}
		updateConcept(task, loadedConcept, info);
		return CHANGE_MADE;
	}
	
	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = null;
		Matcher matcher = descIdCompIdPattern.matcher(lineItems[0]);
		if (matcher.find()) {
			String descId = matcher.group(1);
			String compId = matcher.group(2);

			c = gl.getConcept(compId);
			Description d = gl.getDescription(descId);
			List<Description> descs = conceptDescriptionsToProcess.computeIfAbsent(c, k -> new ArrayList<>());
			descs.add(d);
		} else {
			LOGGER.warn("Parsing issue for {}", lineItems[0]);
		}
		//OK to return the same concept more than once, because it's stored in a Set
		return c == null ? Collections.emptyList() : Collections.singletonList(c);
	}

}
