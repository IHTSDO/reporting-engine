package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/* INFRA-10432 Update On Examination historical associations based on a spreadsheet.
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateHistoricalAssociationsDriven extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(UpdateHistoricalAssociationsDriven.class);

	private Map<Concept, UpdateAction> replacementMap = new HashMap<>();

	private List<String> targetPrefixes = Arrays.asList(new String[] {"OE ", "CO ", "O/E", "C/O", "Complaining of", "On examination"});

	private List<String> skipConcepts = Arrays.asList(new String[]{"163092009",
			"163094005",
			"163096007",
			"164018003",
			"275284008",
	});
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		UpdateHistoricalAssociationsDriven delta = new UpdateHistoricalAssociationsDriven();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.newIdsRequired = false; 
			delta.init(args);
			delta.loadProjectSnapshot(false); 
			delta.postInit();
			delta.process();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Details, Details, , ",
				"Issue, Detail",
				"SCTID, FSN, SemTag, Existing HistAssoc, Lexical Match, Sibling Active, Sibling HistAssoc",
		};

		String[] tabNames = new String[]{
				"Delta Records Created",
				"Other processing issues",
				"Additional Concepts"
		};
		postInit(tabNames, columnHeadings, false);
	}

	private void process() throws ValidationFailure, TermServerScriptException {
		
		populateReplacementMap();
		populateUpdatedReplacementMap();
		checkForRecentInactivations();
		
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			/*if (!c.getId().equals("164427005")) {
				continue;
			}*/
			//Is this a concept we've been told to replace the associations on?
			if (replacementMap.containsKey(c)) {
				if (!c.isActive()) {
					//Change the Inactivation Indicator
					List<InactivationIndicatorEntry> indicators = c.getInactivationIndicatorEntries(ActiveState.ACTIVE);
					if (indicators.size() != 1) {
						report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept has " + indicators.size() + " active inactivation indicators");
						continue;
					}
					InactivationIndicatorEntry i = indicators.get(0);
					InactivationIndicator prevInactValue = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId());
					//We're going to skip concepts that are currently ambigous and just say what they're set to
					if (prevInactValue.equals(InactivationIndicator.AMBIGUOUS)) {
						String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
						report(c, Severity.MEDIUM, ReportActionType.NO_CHANGE, assocStr, "Supplied: " + replacementMap.get(c));
						continue;
					}

					//Have we got some specific inactivation indicator we're expecting to use?
					InactivationIndicator newII = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
					UpdateAction action = replacementMap.get(c);
					if (action != null && action.inactivationIndicator != null) {
						newII = action.inactivationIndicator;
					}

					if (!action.inactivationIndicator.equals(prevInactValue)) {
						i.setInactivationReasonId(SnomedUtils.translateInactivationIndicator(newII));
						i.setEffectiveTime(null);  //Will mark as dirty
						report(c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, prevInactValue + " --> " + newII);
					}

					replaceHistoricalAssociations(c);
					
				} else {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active");
				}
			}
		}
	}

	private void replaceHistoricalAssociations(Concept c) throws TermServerScriptException {

		UpdateAction action = replacementMap.get(c);
		List<Concept> updatedReplacements = new ArrayList<>(action.replacements);
		for (Concept replacement : action.replacements) {
			//Check that our replacement is still active
			if (!replacement.isActive()) {
				report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Originally suggested replacement is now inactive", replacement);
				Concept updatedReplacement = null;
				try {
					updatedReplacement = getReplacement(PRIMARY_REPORT, c, replacement, false);
				} catch (Exception e) {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Failed to find replacement for inactive replacement" + replacement, e.getMessage());
				}
				if (updatedReplacement == null || !updatedReplacement.isActive()) {
					return;
				}
				updatedReplacements.remove(replacement);
				updatedReplacements.add(updatedReplacement);
			}
		}
		action.replacements = updatedReplacements;

		String prevAssocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		for (AssociationEntry a : c.getAssociationEntries()) {
			if (a.getRefsetId().equals(SCTID_ASSOC_REPLACED_BY_REFSETID)) {
				report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept already using a ReplacedBy association");
				return;
			}
			a.setActive(false); //This will reset effective time and mark as dirty
		}

		String associationTypeSCTID = SnomedUtils.translateAssociation(action.type);
		for (Concept replacement : action.replacements) {
			AssociationEntry assoc = AssociationEntry.create(c, associationTypeSCTID, replacement);
			assoc.setDirty();
			c.getAssociationEntries().add(assoc);
		}
		String newAssocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		report(c, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, prevAssocStr, newAssocStr);
	}

	private void populateReplacementMap() throws TermServerScriptException {
		int lineNo = 0;
		try {
			for (String line : Files.readAllLines(getInputFile().toPath(), Charset.defaultCharset())) {
				try {
					lineNo++;
					String[] items = line.split(TAB);
					Concept inactive = gl.getConcept(items[0]);
					Concept replacement = gl.getConcept(items[2]);
					UpdateAction alreadyMapped = replacementMap.get(inactive);
					UpdateAction thisMapping = createReplacementAssociation(replacement);
					if (alreadyMapped != null && !alreadyMapped.equals(replacement)) {
						report(SECONDARY_REPORT,"Map replacement for inactive " + inactive + " already seen.  Was " + alreadyMapped + " now " + thisMapping);
					}
					replacementMap.put(inactive, thisMapping);
				} catch (Exception e) {
					report(SECONDARY_REPORT,"Failed to parse line (1st file) at line " + lineNo + ": " + line + " due to " + e.getMessage());
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private void populateUpdatedReplacementMap() throws TermServerScriptException {
		int lineNo = 0;
		try {
			for (String line : Files.readAllLines(getInputFile(1).toPath(), Charset.defaultCharset())) {
				try {
					lineNo++;
					String[] items = line.split(TAB);
					Concept inactive = gl.getConcept(items[0]);
					String inactivationIndicatorStr = items[2];
					String replacementsStr = items[3];
					InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
					Association association = Association.REPLACED_BY;
					if (inactivationIndicatorStr.contains("Ambiguous")) {
						inactivationIndicator = InactivationIndicator.AMBIGUOUS;
						association = Association.POSS_EQUIV_TO;
					}
					List<Concept> replacements = splitConcepts(replacementsStr);
					UpdateAction action = new UpdateAction();
					action.inactivationIndicator = inactivationIndicator;
					action.replacements = replacements;
					action.type = association;
					replacementMap.put(inactive, action);
				} catch (Exception e) {
					report(SECONDARY_REPORT,"Failed to parse line (2nd file) at line " + lineNo + ": " + line + " due to " + e.getMessage());
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private List<Concept> splitConcepts(String replacementsStr) {
		String[] conceptIds = replacementsStr.split(",");
		return Arrays.stream(conceptIds).map(s -> gl.getConceptSafely(s)).collect(Collectors.toList());
	}

	private void checkForRecentInactivations() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.getFSNDescription() == null) {
				LOGGER.warn("Unpopulated " + c.getId());
				continue;
			}
			if (c.isActive() || !inScope(c) ||
					skipConcepts.contains(c.getId()) ||
					replacementMap.containsKey(c)) {
				continue;
			}
			//Can we find a sibling for this concept?
			Concept sibling = findSibling(c);
			String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
			if (sibling != null) {
				if (sibling.isActive()) {
					report(TERTIARY_REPORT, c, assocStr, sibling, "Y");
				} else {
					report(TERTIARY_REPORT, c, assocStr, sibling, "N", SnomedUtils.prettyPrintHistoricalAssociations(sibling, gl));
				}
			} else {
				report(TERTIARY_REPORT, c, assocStr);
			}
		}
	}

	private Concept findSibling(Concept c) throws TermServerScriptException {
		String targetFsn = "Unknown";
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				targetFsn = c.getFsn().replace(targetPrefix, "").replace("- ", "").trim();
				targetFsn = SnomedUtils.deconstructFSN(targetFsn)[0];
				break;
			}
		}

		for (Concept sibling : gl.getAllConcepts()) {
			for (Description d : sibling.getDescriptions()) {
				if (targetFsn.equalsIgnoreCase(d.getTerm())) {
					return sibling;
				}
			}
		}
		return null;
	}

	private boolean inScope(Concept c) {
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				return true;
			}
		}
		return false;
	}

	private UpdateAction createReplacementAssociation(Concept replacement) {
		UpdateAction action = new UpdateAction();
		action.type = Association.REPLACED_BY;
		action.inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
		action.replacements = Arrays.asList(replacement);
		return action;
	}

	class UpdateAction {
		Association type;
		InactivationIndicator inactivationIndicator;
		List<Concept> replacements;

		public String toString() {
			return inactivationIndicator + ": " + type + " " + replacements.stream().map(c -> c.toString()).collect(Collectors.joining(", "));
		}
	}

}
