package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;


/**
 * RP-605 List any concepts (which will almost certainly be inactive) where the FSN
 * does not contain a currently valid semantic tag.
 */
public class MissingSemanticTags extends BatchFix {

	private List<String> historicallyAcceptableSemTags = List.of("(administrative concept)",
			"(context-dependent category)",
			"(environment / location)",
			"(special concept)");

	private Map<String, String> knownReplacements = Map.of(
		"(virtual clinical drug)", "(clinical drug)",
		"(biological function)","(substance)",
		"(inactive concept)", "(foundation metadata concept)"
	);

	private Set<String> validSemTags = new HashSet<>();

	protected MissingSemanticTags(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		MissingSemanticTags fix = new MissingSemanticTags(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags
		for (Concept topLevel : ROOT_CONCEPT.getDescendants(IMMEDIATE_CHILD)) {
			Set<Concept> descendants = topLevel.getDescendants(NOT_SET);
			for (Concept thisDescendent : descendants) {
				validSemTags.add(SnomedUtils.deconstructFSN(thisDescendent.getFsn())[1]);
			}
		}
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		if (c.getId().equals("138297002")) {
			debug ("here");
		}
		boolean isReplacement = false;
		String replacementSemTag = "";
		if (knownReplacements.containsKey(semTag)) {
			replacementSemTag = knownReplacements.get(semTag);
			isReplacement = true;
		} else {
			//What are the valid replacements for this semtag?
			List<String> replacementSemTags = getAssocSemTags(c);
			if (replacementSemTags.size() == 1) {
				replacementSemTag = replacementSemTags.get(0);
			} else if (replacementSemTags.size() > 1) {
				//If we have a disorder and a finding, pick the finding
				if (replacementSemTags.contains("(disorder)") && replacementSemTags.contains("(finding)")) {
					replacementSemTag = "(finding)";
				} else {
					throw new TermServerScriptException("Unable to determine replacement for " + semTag + " in " + c);
				}
			}
		}
		List<String> replacementSemTags = getAssocSemTags(c);
		String isA = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_A, ActiveState.BOTH)
				.stream()
				.map(Relationship::toString)
				.collect(Collectors.joining(",\n"));
		String histAssocs = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		changesMade += replaceSemTag(t, c, semTag, replacementSemTag, isReplacement);
		report(c, c.getEffectiveTime(), replacementSemTag, isA, histAssocs);
		return changesMade;
	}

	private int replaceSemTag(Task t , Concept c, String semTag, String replacementSemTag, boolean isReplacement) throws TermServerScriptException {
		String newFSN = c.getFsn();
		if (isReplacement) {
			newFSN = newFSN.replace(semTag, replacementSemTag);
		} else {
			newFSN += " " + replacementSemTag;
		}
		replaceDescription(t, c, c.getFSNDescription(), newFSN, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		return CHANGE_MADE;
	}

	private List<String> getAssocSemTags(Concept c) throws TermServerScriptException {
		List<String> replacementSemTags = new ArrayList<>();
		for (AssociationEntry assoc : c.getAssociationEntries())  {
			if (assoc.isActive()) {
				Concept target = gl.getConcept(assoc.getTargetComponentId());
				replacementSemTags.add(SnomedUtils.deconstructFSN(target.getFsn())[1]);
			}
		}
		return replacementSemTags;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		nextConcept:
		//Now work through all Concepts and list any that don't have an active semantic tag
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			if (!validSemTags.contains(semTag) &&
					!historicallyAcceptableSemTags.contains(semTag)) {
				process.add(c);
			}
		}
		return process;
	}
}
