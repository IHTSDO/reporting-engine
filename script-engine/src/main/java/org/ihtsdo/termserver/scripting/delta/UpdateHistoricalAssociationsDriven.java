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
	private Set<Concept> updatedConcepts = new HashSet<>();
	private Set<Concept> skipConcepts = new HashSet<>();

	private List<String> targetPrefixes = Arrays.asList(new String[] {"OE ", "CO ", "O/E", "C/O", "Complaining of", "On examination"});

	private List<String> manageManually = Arrays.asList(new String[]{
			"163092009","163094005","163096007","164018003","275284008",
			"271879001","164075007","162952003","164398007","164346005",
			"141331007","141369002","141751009","140614001","140514005",
			"140605006","140761009","141666001","141492001","141849001",
			"141853004","141870007","141874003","141857003","141864001",
			"141819003","140962005","163432001"
	});

	private Map<Concept, List<String>> normalisedDescriptionMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		UpdateHistoricalAssociationsDriven delta = new UpdateHistoricalAssociationsDriven();
		try {
			ReportSheetManager.targetFolderId = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"; //Technical Specialist
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
				"SCTID, FSN, SemTag, Notes, Replacement Mismatch, Existing Inact / HistAssoc, Sibling Lexical Match, Sibling Active, Sibling Already in Delta, Sibling HistAssoc, Cousin Lexical Match, Cousin Active, Cousin HistAssoc, Cathy Notes, ",
				/*"SCTID, FSN, SemTag, Effective Time, Existing Inact / HistAssoc, Mentioned in Tab 3",
				"SCTID, FSN, SemTag, Effective Time, Existing Inact / HistAssoc",*/
				"SCTID, FSN, SemTag, ConceptGroup, Still Active, Updated, Final State"
		};

		String[] tabNames = new String[]{
				"Delta Records Created",
				"Other processing issues",
				"Additional Inactive Concepts",
				/*"Concepts moved elsewhere",
				"Inactivated without indicator",*/
				"Final State"
		};
		postInit(tabNames, columnHeadings, false);
	}

	private void process() throws ValidationFailure, TermServerScriptException {
		populateCathyNotes();
		populateSkipList();
		populateReplacementMap();
		populateUpdatedReplacementMap();
		populateNormalisedDescriptionMap();
		checkForRecentInactivations();

		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			/*if (!c.getId().equals("164427005")) {
				continue;
			}*/
			//Is this a concept we've been told to replace the associations on?
			if (replacementMap.containsKey(c)) {
				if (!c.isActive()) {
					processConcept(c);
				} else {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active");
				}
			}
		}
		
		doFinalStateTab();
	}

	private void populateNormalisedDescriptionMap() {
		for (Concept c : gl.getAllConcepts()) {
			List<String> thisConceptTerms = new ArrayList<>();
			normalisedDescriptionMap.put(c, thisConceptTerms);
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String thisTerm = d.getTerm();
				if (d.getType().equals(DescriptionType.FSN)) {
					thisTerm = SnomedUtils.deconstructFSN(thisTerm, true)[0];
				}
				thisTerm = thisTerm.replace("-", "").replaceAll("  ", " ");
				thisConceptTerms.add(thisTerm);
			}
		}
	}

	private void processConcept(Concept c) throws TermServerScriptException {
		if (c.getId().equals("164488002")) {
			LOGGER.info("Debug here");
		}

		//Change the Inactivation Indicator
		List<InactivationIndicatorEntry> indicators = c.getInactivationIndicatorEntries(ActiveState.ACTIVE);
		if (indicators.size() != 1) {
			report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept has " + indicators.size() + " active inactivation indicators");
			return;
		}

		InactivationIndicatorEntry i = indicators.get(0);
		InactivationIndicator prevInactValue = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId());

		//Have we got some specific inactivation indicator we're expecting to use?
		InactivationIndicator newII = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
		UpdateAction action = replacementMap.get(c);
		if (action != null && action.inactivationIndicator != null) {
			newII = action.inactivationIndicator;
		}

		if (!action.inactivationIndicator.equals(prevInactValue)) {
			i.setInactivationReasonId(SnomedUtils.translateInactivationIndicator(newII));
			i.setEffectiveTime(null);  //Will mark as dirty
			c.setInactivationIndicator(newII);
			report(c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, prevInactValue + " --> " + newII, "");
			updatedConcepts.add(c);
		}

		if (!action.associationTypeUnchanged || !action.associationTargetsUnchanged) {
			replaceHistoricalAssociations(c);
		}
	}

	private void replaceHistoricalAssociations(Concept c) throws TermServerScriptException {
		UpdateAction action = replacementMap.get(c);
		Set<Concept> updatedReplacements = updateReplacementsIfRequired(c, action);

		String prevAssocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		for (AssociationEntry a : c.getAssociationEntries()) {
			if (a.getRefsetId().equals(SCTID_ASSOC_REPLACED_BY_REFSETID)) {
				report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept already using a ReplacedBy association");
				return;
			}
			a.setActive(false); //This will reset effective time and mark as dirty
		}

		String associationTypeSCTID;
		//If we have a NCEP then the assocation type must be "Replaced By"
		if (action.inactivationIndicator.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			associationTypeSCTID = SCTID_ASSOC_REPLACED_BY_REFSETID;
		} else {
			associationTypeSCTID = SnomedUtils.translateAssociation(action.type);
		}

		for (Concept replacement : updatedReplacements) {
			AssociationEntry assoc = AssociationEntry.create(c, associationTypeSCTID, replacement);
			assoc.setDirty();
			c.getAssociationEntries().add(assoc);
		}
		String newAssocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		updatedConcepts.add(c);
		report(c, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, prevAssocStr, newAssocStr);
	}

	private Set<Concept> updateReplacementsIfRequired(Concept c, UpdateAction action) throws TermServerScriptException {
		Set<Concept> updatedReplacements;
		//Were we told to keep the existing association targets?
		if (action.associationTargetsUnchanged) {
			updatedReplacements = SnomedUtils.getHistoricalAssocationTargets(c, gl);
		} else {
			updatedReplacements = new HashSet<>(action.replacements);
		}

		//Loop around a copy of this collection so we can safely modify the orignal
		for (Concept replacement : new HashSet<>(updatedReplacements)) {
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
					return updatedReplacements;
				}
				updatedReplacements.remove(replacement);
				updatedReplacements.add(updatedReplacement);
			}
		}
		return updatedReplacements;
	}

	private void populateReplacementMap() throws TermServerScriptException {
		int lineNo = 0;
		try {
			for (String line : Files.readAllLines(getInputFile().toPath(), Charset.defaultCharset())) {
				try {
					lineNo++;
					if (line.startsWith("#")) {
						report(SECONDARY_REPORT,"Skipped line (commented out) " + lineNo + ": " + line, getInputFile().toPath());
						continue;
					}
					String[] items = line.split(TAB);
					Concept inactive = gl.getConcept(items[0]);
					Concept replacement = gl.getConcept(items[2]);
					UpdateAction alreadyMapped = replacementMap.get(inactive);
					UpdateAction thisMapping = createReplacementAssociation(replacement);
					if (alreadyMapped != null && !alreadyMapped.replacements.contains(replacement)) {
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
					} else if (inactivationIndicatorStr.contains("Outdated")) {
						inactivationIndicator = InactivationIndicator.OUTDATED;
						association = Association.REPLACED_BY;
					} else if (inactivationIndicatorStr.contains("CDC")) {
						inactivationIndicator = InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT;
						association = Association.REPLACED_BY;
					}

					UpdateAction action = new UpdateAction();
					action.inactivationIndicator = inactivationIndicator;
					replacementMap.put(inactive, action);

					if (replacementsStr.equals("UNCHANGED")) {
						action.associationTargetsUnchanged = true;
					} else {
						Set<Concept> replacements = splitConcepts(replacementsStr);
						action.replacements = replacements;
						action.type = association;
					}

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
						//If we're saying the same thing, it's cool
						if (cathyNotes.get(c).equals(notes)) {
							continue;
						}
						notes = cathyNotes.get(c) + "\n" + notes;
					}
					cathyNotes.put(c, notes);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + getInputFile(2) + " at line " + lineNo,e);
		}
	}
	
	private void populateSkipList() throws TermServerScriptException {
		int lineNo = 0;
		try {
			for (String line : Files.readAllLines(getInputFile(3).toPath(), Charset.defaultCharset())) {
					lineNo++;
					String[] items = line.split(TAB);
					Concept c = gl.getConcept(items[0]);
					skipConcepts.add(c);
					String notes = "Told to skip";
					if (cathyNotes.containsKey(c)) {
						notes = cathyNotes.get(c) + "\n" + notes;
					}
					cathyNotes.put(c, notes);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + getInputFile(3) + " at line " + lineNo,e);
		}
	}

	private Set<Concept> splitConcepts(String replacementsStr) {
		String[] conceptIds = replacementsStr.split(",");
		return Arrays.stream(conceptIds).map(s -> gl.getConceptSafely(s)).collect(Collectors.toSet());
	}

	private void checkForRecentInactivations() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.getFSNDescription() == null) {
				LOGGER.warn("Unpopulated " + c.getId());
				continue;
			}
			if (c.isActive()) {
				continue;
			}
			if (!inScope(c) ||
					manageManually.contains(c.getId()) ||
					replacementMap.containsKey(c) ||
					reportedAsAdditional.contains(c)) {
				/*if (c.getInactivationIndicator() == null) {
					report(QUINARY_REPORT, c, c.getEffectiveTime(), SnomedUtils.prettyPrintHistoricalAssociations(c, gl, true));
				} else if (c.getInactivationIndicator().equals(InactivationIndicator.MOVED_ELSEWHERE)) {
					//We're also going to report concepts that are not OE/CO but are marked as "Moved To"
					report(QUATERNARY_REPORT, c, c.getEffectiveTime(), SnomedUtils.prettyPrintHistoricalAssociations(c, gl, true));
				}*/
				continue;
			}
			String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl, true);
			List<String> notes = new ArrayList<>();
			checkForProcessInclusion("Self", c, notes);

			//Can we find a sibling for this concept - same text with different prefix?
			Concept sibling = findSibling(c);
			String[] siblingData = new String[] {"", "", "", ""};
			if (sibling != null) {
				checkForProcessInclusion("Sibling", sibling, notes);
				reportedAsAdditional.add(sibling);
				siblingData[0] = sibling.toString();
				siblingData[1] = sibling.isActive()?"Y":"N";
				siblingData[2] = replacementMap.containsKey(sibling)?"Y":"N";
				siblingData[3] = SnomedUtils.prettyPrintHistoricalAssociations(sibling, gl, true);
			}

			Concept cousin = findCousin(c, true);
			String[] cousinData = new String[] {"", "", ""};
			if (cousin != null) {
				checkForProcessInclusion("Cousin", cousin, notes);
				cousinData[0] = cousin.toString();
				cousinData[1] = cousin.isActive()?"Y":"N";
				cousinData[2] = SnomedUtils.prettyPrintHistoricalAssociations(cousin, gl, true);
			}
			String mismatchFlag = hasMismatchedAssociations(c, sibling, cousin)?"Y":"N";
			String notesStr = notes.stream().collect(Collectors.joining(",\n"));
			report(TERTIARY_REPORT, c, notesStr, mismatchFlag, assocStr, siblingData, cousinData);
		}
	}

	private void checkForProcessInclusion(String familyRelationship, Concept c, List<String> notes) {
		//For 'NOS' concepts, we may pull them into our main processing set
		if (c.getFsn().contains("NOS")) {
			checkForProcessInclusionNOS(familyRelationship, c, notes);
		} else if (c.FSN.contains("context-dependent")) {
			checkForProcessInclusionCDC(familyRelationship, c, notes, true);
		} else if (c.FSN.contains("[")) {
			checkForProcessInclusionCDC(familyRelationship, c, notes,false);
		} else {
			checkForProcessInclusionAmbiguous(familyRelationship, c, notes);
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

	private void checkForProcessInclusionNOS(String familyRelationship, Concept c, List<String> notes) {
		if (hasCommonReasonNotToInclude(familyRelationship, c, notes)) {
			return;
		}

		//Do we have one association target or more?
		Set<Concept> assocTargets = c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_POSS_EQUIV_REFSETID, true).stream()
				.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
				.collect(Collectors.toSet());

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
			notes.add(familyRelationship + " not included for NOS processing due to lack of existing associations");
			return;
		}
		replacementMap.put(c, action);
		notes.add(familyRelationship + " included for processing - CDC");
	}

	private void checkForProcessInclusionCDC(String familyRelationship, Concept c, List<String> notes, boolean allowMultipleTargets) {
		//The CDC here stands for Context Dependent Category
		if (hasCommonReasonNotToInclude(familyRelationship, c, notes)) {
			return;
		}

		if (!c.getInactivationIndicator().equals(InactivationIndicator.AMBIGUOUS)) {
			notes.add(familyRelationship + " not included for processing - inactivation indicator is not Ambiguous");
			return;
		}
		//Do we have one association target or more?
		Set<Concept> assocTargets = c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_POSS_EQUIV_REFSETID, true).stream()
				.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
				.collect(Collectors.toSet());

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
				notes.add(familyRelationship + " not included for processing due to multiple existing associations");
				return;
			}
		} else {
			notes.add(familyRelationship + " not included for processing due to lack of existing associations");
		}
		replacementMap.put(c, action);
		notes.add(familyRelationship + " included for processing - NCEP");
	}
	
	private void checkForProcessInclusionAmbiguous(String familyRelationship, Concept c, List<String> notes) {
		if (hasCommonReasonNotToInclude(familyRelationship, c, notes)) {
			return;
		}

		if (c.getInactivationIndicator() ==  null) {
			notes.add(familyRelationship + " check original state. No inactivation indicator specified?");
		} else if (!c.getInactivationIndicator().equals(InactivationIndicator.AMBIGUOUS)) {
			return;
		}

		//Do we have exactly one historical association?  We can change this to 
		//NCEP and Replaced By if so
		Set<Concept> assocTargets = c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_POSS_EQUIV_REFSETID, true).stream()
				.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
				.collect(Collectors.toSet());

		if (assocTargets.isEmpty()) {
			//Do we in fact already have a REPLACED_BY ?  Just need to set the inactivation indicator in that case
			if (!c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_REPLACED_BY_REFSETID, true).isEmpty()) {
				UpdateAction action = new UpdateAction().withInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)
														.withAssociationsUnchanged();
				replacementMap.put(c, action);
				notes.add(familyRelationship + " included for processing - no inactivation indicator with REPLACED_BY association(s)");
			} else {
				LOGGER.warn("Check odd situation here");
			}
		} else if (assocTargets.size() == 1) {
			UpdateAction action = new UpdateAction()
					.withAssociationType(Association.REPLACED_BY)
					.withInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)
					.withReplacements(assocTargets);
			replacementMap.put(c, action);
			notes.add(familyRelationship + " included for processing - ambiguous with 1 x association");
		} else if (c.getInactivationIndicator() == null) {
			//Got the situation where we have multiple poss equiv to, but no inactivation indicator, so just set that.
			UpdateAction action = new UpdateAction()
					.withInactivationIndicator(InactivationIndicator.AMBIGUOUS)
					.withAssociationsUnchanged();
			notes.add(familyRelationship + " included for processing - no inactivation indicator (setting to Ambiguous) with unchanged multiple poss equiv to association(s)");
			replacementMap.put(c, action);
		} else {
			notes.add(familyRelationship + " not included for processing due to multiple associations");
		}
	}

	private boolean hasCommonReasonNotToInclude(String familyRelationship, Concept c, List<String> notes) {
		if (c.isActiveSafely()) {
			notes.add(familyRelationship + " not included for processing - active");
			return true;
		}

		//Are we already processing this concept?
		if (replacementMap.containsKey(c)) {
			notes.add(familyRelationship + " already included for processing");
			return true;
		}

		//Is this concept in fact already fine?
		if (c.getInactivationIndicator() != null &&
				c.getInactivationIndicator().equals(InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT)) {
			notes.add( familyRelationship + " not included for processing - already inactivated as CDC.");
			return true;
		}
		return false;
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
		
		List<String> targetFSNs = new ArrayList<>();
		
		//Check for all alternative prefixes
		//Pre-generate these so we're not doing that inside the loop with all concepts
		for (String siblingPrefix : targetPrefixes) {
			if (siblingPrefix.equals(thisPrefix)) {
				continue;
			}

			String targetFsn = siblingPrefix + " " + rootFsn;
			targetFsn = targetFsn.replace("  ", " ").trim();
			targetFSNs.add(targetFsn);
		}

		for (Concept sibling : gl.getAllConcepts()) {
			//Don't compare with self
			if (sibling.equals(c)) {
				continue;
			}
			
			for (String thisTerm : normalisedDescriptionMap.get(sibling)) {
				for (String targetFsn : targetFSNs) {
					if (targetFsn.equalsIgnoreCase(thisTerm)) {
						return sibling;
					}
				}
			}
		}
		return null;
	}

	//A cousin is the same basic FSN, but without any prefix
	private Concept findCousin(Concept c, boolean includeActive) throws TermServerScriptException {
		String targetFsn = "Unknown";
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				targetFsn = c.getFsn().replace(targetPrefix, "").replace("- ", "").trim();
				targetFsn = SnomedUtils.deconstructFSN(targetFsn)[0];
				break;
			}
		}

		for (Concept cousin : gl.getAllConcepts()) {
			//Don't include active concepts if we said not to
			//Also, don't compare with self
			if ((!includeActive && cousin.isActiveSafely()) ||
				cousin.equals(c)) {
				continue;
			}

			for (String term : normalisedDescriptionMap.get(cousin)) {
				if (targetFsn.equalsIgnoreCase(term)) {
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
		action.replacements = Set.of(replacement);
		return action;
	}

	protected boolean report (Concept c, Object...details) throws TermServerScriptException {
		if (cathyNotes.containsKey(c)) {
			return report(PRIMARY_REPORT, c, details, cathyNotes.get(c));
		} else {
			return report(PRIMARY_REPORT, c, details);
		}
	}

	private void doFinalStateTab() throws TermServerScriptException {
		Set<Concept> finalStateConceptsReported = new HashSet<>();
		int conceptGroup = 0;
		for (Concept c : SnomedUtils.sort(updatedConcepts)) {
			if (finalStateConceptsReported.contains(c)) {
				continue;
			}
			conceptGroup++;
			finalTabReport(c, conceptGroup, finalStateConceptsReported);
			finalTabReport(findSibling(c), conceptGroup, finalStateConceptsReported);
			finalTabReport(findCousin(c, false), conceptGroup, finalStateConceptsReported);
		}
	}

	private void finalTabReport(Concept c, int conceptGroup, Set<Concept> finalStateConceptsReported) throws TermServerScriptException {
		if (c == null) {
			return;
		}
		String updated = updatedConcepts.contains(c) ? "Y" : "N";
		String active = SnomedUtils.translateActiveState(c);
		report(QUATERNARY_REPORT, c, conceptGroup, active, updated, SnomedUtils.prettyPrintHistoricalAssociations(c, gl, true));
		finalStateConceptsReported.add(c);
	}

	class UpdateAction {
		public boolean associationTargetsUnchanged = false;
		public boolean associationTypeUnchanged = false;
		Association type;
		InactivationIndicator inactivationIndicator;
		Set<Concept> replacements;

		public String toString() {
			String replacementsStr = "UNCHANGED";
			if (this.replacements != null) {
				replacementsStr = this.replacements.stream().map(c -> c.toString()).collect(Collectors.joining(","));
			}
			return inactivationIndicator + ": " + type + " " + replacementsStr;
		}

		public UpdateAction withInactivationIndicator(InactivationIndicator inactivationIndicator) {
			this.inactivationIndicator = inactivationIndicator;
			return this;
		}

		public UpdateAction withReplacements(Set<Concept> replacements) {
			this.replacements = replacements;
			return this;
		}

		public UpdateAction withAssociationType(Association type) {
			this.type = type;
			return this;
		}

		public UpdateAction withAssociationsUnchanged() {
			this.associationTargetsUnchanged = true;
			this.associationTypeUnchanged = true;
			return this;
		}
	}

}
