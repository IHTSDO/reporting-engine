package org.ihtsdo.termserver.scripting.delta;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.RF2Constants;
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
	private Map<Concept, String> cathyNotes = new HashMap<>();
	private Set<Concept> reportedAsAdditional = new HashSet<>();

	private List<String> targetPrefixes = Arrays.asList(new String[] {"OE ", "CO ", "O/E", "C/O", "Complaining of", "On examination"});

	private List<String> manageManually = Arrays.asList(new String[]{
			"163092009","163094005","163096007","164018003","275284008",
			"271879001","164075007","162952003","164398007","164346005",
			"141331007","141369002","141751009","140614001","140514005",
			"140605006","140761009","141666001","141492001","141849001",
			"141853004","141870007","141874003","141857003","141864001",
			"141819003","140962005","163432001"
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
				"SCTID, FSN, SemTag, Severity, Action, Details, Details, Cathy Notes, , ",
				"Issue, Detail",
				"SCTID, FSN, SemTag, Notes, Replacement Mismatch, Existing Inact / HistAssoc, Sibling Lexical Match, Sibling Active, Sibling Already in Delta, Sibling HistAssoc, Cousin Lexical Match, Cousin Active, Cousin HistAssoc, Cathy Notes, "
		};

		String[] tabNames = new String[]{
				"Delta Records Created",
				"Other processing issues",
				"Additional Inactive Concepts"
		};
		postInit(tabNames, columnHeadings, false);
	}

	private void process() throws ValidationFailure, TermServerScriptException {
		
		populateReplacementMap();
		populateUpdatedReplacementMap();
		checkForRecentInactivations();
		populateCathyNotes();
		
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

	private void populateCathyNotes() throws TermServerScriptException {
		int lineNo = 0;
		try {
			for (String line : Files.readAllLines(getInputFile(2).toPath(), Charset.defaultCharset())) {
					lineNo++;
					String[] items = line.split(TAB);
					Concept c = gl.getConcept(items[0]);
					String notes = items[1];
					if (items.length == 3) {
						notes += " " + items[2];
					}
					if (cathyNotes.containsKey(c)) {
						throw new TermServerScriptException("Notes for " + c + " already seen.  Was " + cathyNotes.get(c) + " now " + notes);
					}
					cathyNotes.put(c, notes);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + getInputFile(3),e);
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
			if (c.isActive() ||
					!inScope(c) ||
					manageManually.contains(c.getId()) ||
					replacementMap.containsKey(c) ||
					reportedAsAdditional.contains(c)) {
				continue;
			}
			String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
			String notes = "";
			//For 'NOS' concepts, we may pull them into our main processing set
			if (c.getFsn().contains("NOS")) {
				notes = checkForProcessInclusionNOS(c);
			} else if (c.FSN.contains("context-dependent")) {
				notes = checkForProcessInclusion(c, true);
			} else if (c.FSN.contains("[")) {
				notes = checkForProcessInclusion(c, false);
			}

			//Can we find a sibling for this concept - same text with different prefix?
			Concept sibling = findSibling(c);
			String[] siblingData = new String[] {"", "", "", ""};
			if (sibling != null) {
				reportedAsAdditional.add(sibling);
				siblingData[0] = sibling.toString();
				siblingData[1] = sibling.isActive()?"Y":"N";
				siblingData[2] = replacementMap.containsKey(sibling)?"Y":"N";
				siblingData[3] = SnomedUtils.prettyPrintHistoricalAssociations(sibling, gl, true);
			}

			Concept cousin = findCousin(c);
			String[] cousinData = new String[] {"", "", ""};
			if (cousin != null) {
				cousinData[0] = cousin.toString();
				cousinData[1] = cousin.isActive()?"Y":"N";
				cousinData[2] = SnomedUtils.prettyPrintHistoricalAssociations(cousin, gl, true);
			}
			String mismatchFlag = hasMismatchedAssociations(c, sibling, cousin)?"Y":"N";
			report(TERTIARY_REPORT, c, notes, mismatchFlag, assocStr, siblingData, cousinData);
		}
	}

	private boolean hasMismatchedAssociations(Concept... concepts) throws TermServerScriptException {
		//Do these concepts all have the same historical associations?
		Set<Concept> targets = new HashSet<>();
		for (Concept c : concepts) {
			if (c == null) {
				continue;
			}
			if (targets.isEmpty()) {
				targets = SnomedUtils.getHistoricalAssocationTargets(c, gl);
			} else {
				Set<Concept> theseTargets = SnomedUtils.getHistoricalAssocationTargets(c, gl);
				if (!targets.equals(theseTargets)) {
					return true;
				}
			}
		}
		return false;
	}

	private String checkForProcessInclusionNOS(Concept c) {
		//Are we already processing this concept?
		if (replacementMap.containsKey(c)) {
			return "Already included for processing";
		}
		//Do we have one association target or more?
		List<Concept> assocTargets = c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_POSS_EQUIV_REFSETID).stream()
				.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
				.collect(Collectors.toList());

		UpdateAction action = null;
		if (assocTargets.size() == 1) {
			action = new UpdateAction()
					.withAssociationType(Association.REPLACED_BY)
					.withInactivationIndicator(InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT)
					.withReplacements(assocTargets);
		} else if (assocTargets.size() > 1) {
			action = new UpdateAction()
					.withAssociationType(Association.PARTIALLY_EQUIV_TO)
					.withInactivationIndicator(InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT)
					.withReplacements(assocTargets);
		} else {
			return "Not included for processing due to lack of existing associations";
		}
		replacementMap.put(c, action);
		return "Included for processing - CDC";
	}

	private String checkForProcessInclusion(Concept c, boolean allowMultipleTargets) {
		//The CDC here stands for Context Dependent Category
		//Are we already processing this concept?
		if (replacementMap.containsKey(c)) {
			return "Already included for processing";
		}

		if (!c.getInactivationIndicator().equals(InactivationIndicator.AMBIGUOUS)) {
			return "Not included for processing - inactivation indicator is not Ambiguous";
		}
		//Do we have one association target or more?
		List<Concept> assocTargets = c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_POSS_EQUIV_REFSETID).stream()
				.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
				.collect(Collectors.toList());

		UpdateAction action = null;
		if (assocTargets.size() == 1) {
			action = new UpdateAction()
					.withAssociationType(Association.REPLACED_BY)
					.withInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)
					.withReplacements(assocTargets);
		} else if (assocTargets.size() > 1) {
			if (allowMultipleTargets) {
				action = new UpdateAction()
						.withAssociationType(Association.PARTIALLY_EQUIV_TO)
						.withInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)
						.withReplacements(assocTargets);
			} else {
				return "Not included for processing due to multiple existing associations";
			}
		} else {
			return "Not included for processing due to lack of existing associations";
		}
		replacementMap.put(c, action);
		return "Included for processing - NCEP";
	}

	//A cousin is the same basic FSN, but with a _different_ prefix
	private Concept findSibling(Concept c) throws TermServerScriptException {
		String rootFsn = "Unknown";
		String thisPrefix = "Unknown";
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				rootFsn = c.getFsn().replace(targetPrefix, "").replace("- ", "").trim();
				rootFsn = SnomedUtils.deconstructFSN(rootFsn)[0];
				thisPrefix = targetPrefix;
				break;
			}
		}

		for (Concept sibling : gl.getAllConcepts()) {
			for (Description d : sibling.getDescriptions()) {
				String thisTerm = d.getTerm();
				if (d.getType().equals(DescriptionType.FSN)) {
					thisTerm = SnomedUtils.deconstructFSN(thisTerm, true)[0];
				}
				thisTerm = thisTerm.replace("-", "").replaceAll("  ", " ");
				//Check for all alternative prefixes
				for (String siblingPrefix : targetPrefixes) {
					if (siblingPrefix.equals(thisPrefix)) {
						continue;
					}

					String targetFsn = siblingPrefix + " " + rootFsn;
					targetFsn = targetFsn.replace("  ", " ").trim();

					if (targetFsn.equalsIgnoreCase(thisTerm)) {
						return sibling;
					}
				}
			}
		}
		return null;
	}

	//A cousin is the same basic FSN, but without any prefix
	private Concept findCousin(Concept c) throws TermServerScriptException {
		String targetFsn = "Unknown";
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				targetFsn = c.getFsn().replace(targetPrefix, "").replace("- ", "").trim();
				targetFsn = SnomedUtils.deconstructFSN(targetFsn)[0];
				break;
			}
		}

		for (Concept cousin : gl.getAllConcepts()) {
			for (Description d : cousin.getDescriptions()) {
				if (targetFsn.equalsIgnoreCase(d.getTerm())) {
					return cousin;
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

	protected boolean report (Concept c, Object...details) throws TermServerScriptException {
		if (cathyNotes.containsKey(c)) {
			return report(PRIMARY_REPORT, c, details, cathyNotes.get(c));
		} else {
			return report(PRIMARY_REPORT, c, details);
		}
	}

	class UpdateAction {
		Association type;
		InactivationIndicator inactivationIndicator;
		List<Concept> replacements;

		public String toString() {
			return inactivationIndicator + ": " + type + " " + replacements.stream().map(c -> c.toString()).collect(Collectors.joining(", "));
		}

		public UpdateAction withInactivationIndicator(InactivationIndicator inactivationIndicator) {
			this.inactivationIndicator = inactivationIndicator;
			return this;
		}

		public UpdateAction withReplacements(List<Concept> replacements) {
			this.replacements = replacements;
			return this;
		}

		public UpdateAction withAssociationType(Association type) {
			this.type = type;
			return this;
		}
	}

}
