package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/*
 * Deletes Descriptions and recreates them identically with new SCTIDS
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceDescriptionIds extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceDescriptionIds.class);

	Set<String> descIds = new HashSet<>();
	protected ReplaceDescriptionIds(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceDescriptionIds fix = new ReplaceDescriptionIds(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		loadDescIds();
	}
	
	private void loadDescIds() throws TermServerScriptException {
		try {
			List<String> lines = Files.readLines(getInputFile(), Charsets.UTF_8);
			LOGGER.info("Loading description ids from " + getInputFile());
			for (String line : lines) {
				descIds.add(line);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load description ids",e);
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceDescriptionIds(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int replaceDescriptionIds(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : concept.getDescriptions()) {
			for (String descId : descIds) {
				if (d.getDescriptionId()!= null && d.getDescriptionId().equals(descId)) {
					//Remove the descriptionId, thus making it be recreated 
					if (d.getEffectiveTime() != null) {
						report(task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Attempted to delete published description: " + d);
					} else {
						String oldDescId = d.getDescriptionId();
						d.setDescriptionId(null);
						changesMade++;
						report(task, concept, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Replaced description id for SCTID: " + oldDescId + " - " + d.getTerm());
						if (!d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) && !StringUtils.isCaseSensitive(d.getTerm())) {
							report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Check why term marked case sensitive");
						}
					}
				}
			}
		}
		return changesMade;
	}

	protected ArrayList<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		LOGGER.info("Identifying concepts to process");
		GraphLoader gl = GraphLoader.getGraphLoader();
		for (String descId : descIds) {
			Description d = gl.getDescription(descId);
			if (d.getConceptId() != null) {
				allAffected.add(gl.getConcept(d.getConceptId(), false, true));
			} else {
				LOGGER.info (descId + " was not linked to a concept.");
			}
		}
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		return new ArrayList<Component>(allAffected);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
