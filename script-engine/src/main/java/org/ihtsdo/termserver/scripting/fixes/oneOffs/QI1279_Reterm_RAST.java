package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 */
public class QI1279_Reterm_RAST extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI1279_Reterm_RAST.class);

	private Map<String, Concept> substancesByPT;

	protected QI1279_Reterm_RAST(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		QI1279_Reterm_RAST fix = new QI1279_Reterm_RAST(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail, Additional Detail";
			fix.init(args);
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, Severity, Action, Details, , , ",
				"SCTID, FSN, SemTag, Taxonomy, Issue, Expression, Details, , , "};
		String[] tabNames = new String[] {"Processed", "Skip but check"};
		super.postInit(tabNames, columnHeadings, false);
		substancesByPT = SUBSTANCE.getDescendants(NOT_SET).stream()
				.collect(Collectors.toMap(c -> c.getPreferredSynonym(), c -> c));
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		Set<Description> originalDescriptions = SnomedUtils.getDescriptionsList(c, ActiveState.BOTH, false);
		if (SnomedUtils.hasUsGbVariance(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept has US/GB variance");
		}

		//Inactivate any descriptions that contain the word RAST
		for (Description d : originalDescriptions) {
			if (d.isActive() && d.getTerm().contains("RAST")) {
				d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
				d.setActive(false);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d);
				changesMade++;

				//Now, was this the PT?
				if (d.isPreferred() && d.getType().equals(DescriptionType.SYNONYM)) {
					String substanceName = findSubstanceName(t, c);
					report(t, c, Severity.NONE, ReportActionType.INFO, "Substance name detected as: " + substanceName);
					//First try finding a description using X, the organism name
					substanceName = substanceName.replace("immunoglobulin ", "Ig");
					Description promotingDesc = findReplacementDescription(t, c, originalDescriptions, substanceName);
					if (promotingDesc == null) {
						//If that fails, try finding a description without X
						promotingDesc = findReplacementDescription(t, c, originalDescriptions, null);
					}
					if (promotingDesc == null) {
						String msg = "Unable to find replacement PT from: " + SnomedUtils.getDescriptions(c, false);
						throw new TermServerScriptException(msg);
					} else {
						int promotions = SnomedUtils.upgradeTermToPreferred(promotingDesc);
						changesMade += promotions;
						if (promotions > 0) {
							report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Promoted PT: " + promotingDesc);
						}
					}
				} else if (d.getType().equals(DescriptionType.FSN)) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN contains RAST");
				}
			}
		}
		report(t, c, Severity.LOW, ReportActionType.INFO, "Post update descriptions:", SnomedUtils.getDescriptions(c, false));
		return changesMade;
	}

	private Description findReplacementDescription(Task t, Concept c, Set<Description> descs, String organismName) throws TermServerScriptException {
		//First try for an active description, 2nd pass will be inactive
		for (int i=0; i<2; i++) {
			for (Description d : descs) {
				if ((i == 0 && !d.isActive()) || (i == 1 && d.isActive())) {
					continue;
				}
				if (organismName != null && !d.getTerm().startsWith(organismName)) {
					continue;
				}
				if (d.getTerm().contains("specific Ig")) {
					return d;
				}
			}
		}
		return null;
	}

	private String findSubstanceName(Task t, Concept c) throws TermServerScriptException {
		String substanceName = null;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (substancesByPT.values().contains(r.getTarget())) {
				if (substanceName != null) {
					report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept features multiple organisms");
				}
				substanceName = gl.getConcept(r.getTarget().getId()).getPreferredSynonym();
			}
		}
		return substanceName;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();

		nextConcept:
		for (Concept c : SnomedUtils.sort(findConcepts("<104380004 |Allergen specific antibody measurement (procedure)|"))) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().contains("RAST")) {
					process.add(c);
					continue nextConcept;
				}
			}
		}
		return process;
	}



	private String normalise(Description d) {
		return " " + d.getTerm().toLowerCase() + " ";
	}
}
