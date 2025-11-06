package org.ihtsdo.termserver.scripting.delta;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* INFRA-10432 Update On Examination historical associations based on a spreadsheet.
	INFRA-12931 O/E and C/O inactivation reason and historical association changes, continued
*/
public class UpdateHistoricalAssociationsDriven extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(UpdateHistoricalAssociationsDriven.class);

	private static final String UNKNOWN = "Unknown";
	private static final int BATCH_SIZE = 20;

	private Map<Concept, UpdateAction> replacementMap = new HashMap<>();
	private Map<Concept, String> cathyNotes = new HashMap<>();
	private Set<Concept> reportedAsAdditional = new HashSet<>();
	private Set<Concept> updatedConcepts = new HashSet<>();
	private Set<Concept> skipConcepts = new HashSet<>();

	private List<String> targetPrefixes = Arrays.asList("OE ", "CO ", "O/E", "C/O", "Complaining of", "On examination");

	private List<String> manageManually = Arrays.asList(
			"163092009","163094005","163096007","164018003","275284008",
			"271879001","164075007","162952003","164398007","164346005",
			"141331007","141369002","141751009","140614001","140514005",
			"140605006","140761009","141666001","141492001","141849001",
			"141853004","141870007","141874003","141857003","141864001",
			"141819003","140962005","163432001");

	private Map<Concept, List<String>> normalisedDescriptionMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		UpdateHistoricalAssociationsDriven delta = new UpdateHistoricalAssociationsDriven();
		try {
			delta.newIdsRequired = false;
			delta.init(args);
			delta.loadProjectSnapshot(false); 
			delta.postInit(GFOLDER_TECHNICAL_SPECIALIST);
			delta.process();
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Details, Details, Cathy Notes, , ",
				"Issue, Detail, details, details, ,",
				"SCTID, FSN, SemTag, Notes, Replacement Mismatch, Existing Inact / HistAssoc, Sibling Lexical Match, Sibling Active, Sibling Already in Delta, Sibling HistAssoc, Cousin Lexical Match, Cousin Active, Cousin HistAssoc, Cathy Notes, ",
				/*"SCTID, FSN, SemTag, Effective Time, Existing Inact / HistAssoc, Mentioned in Tab 3",
				"SCTID, FSN, SemTag, Effective Time, Existing Inact / HistAssoc",*/
				"SCTID, FSN, SemTag, ConceptGroup, Still Active, Updated, Final State, Cathy Notes"
		};

		String[] tabNames = new String[]{
				"Delta Records Created",
				"Other processing issues",
				"Additional Inactive Concepts",
				/*"Concepts moved elsewhere",
				"Inactivated without indicator",*/
				"Final State"
		};
		super.postInit(googleFolder, tabNames, columnHeadings);
	}

	@Override
	protected void process() throws TermServerScriptException {
		populateCathyNotes();
		populateSkipList();
		populateReplacementMap();
		populateUpdatedReplacementMap();
		populateNormalisedDescriptionMap();
		checkForRecentInactivations();
		processInBatches();
		doFinalStateTab();
	}

	private void processInBatches() throws TermServerScriptException {
		for (List<Concept> batch : formBatches()) {
			for (Concept c : batch) {
				if (!c.isActiveSafely()) {
					processConcept(c);
				} else {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active");
				}
			}
			if (!dryRun) {
				createOutputArchive(true, batch.size());
				outputDirName = "output"; //Reset so we don't end up with _1_1_1
				initialiseOutputDirectory();
				initialiseFileHeaders();
			} else {
				LOGGER.info("Batch of {} processed", batch.size());
			}
			gl.setAllComponentsClean();
		}
	}

	private List<List<Concept>> formBatches() {
		LOGGER.info("Batching {} concepts", replacementMap.size());
		List<List<Concept>> batches = new ArrayList<>();
		List<Concept> remainingConcepts = new ArrayList<>(SnomedUtils.sort(replacementMap.keySet()));
		//We need a copy of this list so we can safely remove from it
		List<Concept> conceptsToProcess = new ArrayList<>(remainingConcepts);
		List<Concept> thisBatch = new ArrayList<>();
		for (Concept c : conceptsToProcess) {
			if (remainingConcepts.contains(c)) {
				thisBatch.add(c);
				remainingConcepts.remove(c);
				//If we've got a sibling or cousin, add them to the batch too
				Concept sibling = findSibling(c);
				if (sibling != null && remainingConcepts.contains(sibling)) {
					thisBatch.add(sibling);
					remainingConcepts.remove(sibling);
				}

				Concept cousin = findCousin(c, true);
				if (cousin != null && remainingConcepts.contains(cousin)) {
					thisBatch.add(cousin);
					remainingConcepts.remove(cousin);
				}

				if (thisBatch.size() >= BATCH_SIZE) {
					batches.add(thisBatch);
					thisBatch = new ArrayList<>();
				}
			}
		}
		LOGGER.info("Batched {} concepts into {} batches", replacementMap.size(), batches.size());
		return batches;
	}


	private void populateNormalisedDescriptionMap() {
		for (Concept c : gl.getAllConcepts()) {
			List<String> thisConceptTerms = new ArrayList<>();
			normalisedDescriptionMap.put(c, thisConceptTerms);
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String thisTerm = d.getTerm();
				if (d.getType().equals(DescriptionType.FSN)) {
					thisTerm = SnomedUtilsBase.deconstructFSN(thisTerm, true)[0];
				}
				thisTerm = thisTerm.replace("-", "")
						.replace("  ", " ");
				thisConceptTerms.add(thisTerm);
			}
		}
	}

	private void processConcept(Concept c) throws TermServerScriptException {
		//Change the Inactivation Indicator
		List<InactivationIndicatorEntry> indicators = c.getInactivationIndicatorEntries(ActiveState.ACTIVE);
		if (indicators.size() != 1) {
			report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept has " + indicators.size() + " active inactivation indicators");
			return;
		}

		if (c.getId().equals("163539007")) {
			LOGGER.info("Debug here");
		}

		InactivationIndicatorEntry i = indicators.get(0);
		InactivationIndicator prevInactValue = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId());

		//Have we got some specific inactivation indicator we're expecting to use?
		InactivationIndicator newII = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
		UpdateAction action = replacementMap.get(c);
		if (action != null && action.inactivationIndicator != null) {
			newII = action.inactivationIndicator;
		} else if (action == null) {
			throw new TermServerScriptException("No action found for " + c);
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
			if (!replacement.isActiveSafely()) {
				report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Originally suggested replacement is now inactive", replacement);
				Concept updatedReplacement = null;
				try {
					updatedReplacement = getReplacement(PRIMARY_REPORT, c, replacement, false);
				} catch (Exception e) {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Failed to find replacement for inactive replacement" + replacement, e.getMessage());
				}
				if (updatedReplacement == null || !updatedReplacement.isActiveSafely()) {
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
				populateReplacementMap(line, lineNo++);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private void populateReplacementMap(String line, int lineNo) throws TermServerScriptException {
		try {
			if (line.startsWith("#")) {
				report(SECONDARY_REPORT,"Skipped line (commented out) " + lineNo + ": " + line, getInputFile().toPath());
				return;
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

	private void populateUpdatedReplacementMap() throws TermServerScriptException {
		int lineNo = 0;
		try {
			for (String line : Files.readAllLines(getInputFile(1).toPath(), Charset.defaultCharset())) {
				populateUpdatedReplacementMap(line, lineNo++);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private void populateUpdatedReplacementMap(String line, int lineNo) throws TermServerScriptException {
		try {
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
			} else if (inactivationIndicatorStr.contains("CDC")) {
				inactivationIndicator = InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT;
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
					report(SECONDARY_REPORT, c, "Concept manually marked as skipped", notes);
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
		LOGGER.info("Checking for recent inactivations...");
		int conceptsChecked = 0;
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (++conceptsChecked % 10000 == 0) {
				LOGGER.info("Checked {} concepts for recent inactivation", conceptsChecked);
			}
           if (shouldBeSkippedForRecentInactivationCheck(c)) {
			   continue;
           }

			List<String> notes = new ArrayList<>();
			boolean alreadyIncludedForProcessed = replacementMap.containsKey(c) || reportedAsAdditional.contains(c);
			boolean addedForProcessing = false;
			String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl, true);

			boolean selfOriginallyNotIncluded = false;
			if (!alreadyIncludedForProcessed) {
				addedForProcessing |= checkForProcessInclusion("Self", c, notes, null, null);
				selfOriginallyNotIncluded = !addedForProcessing;
			}

			//Does the original concept have an action that we could consider for the sibling/cousin?
			UpdateAction action = replacementMap.get(c);
			Concept familyMember = c;

			//Can we find a sibling for this concept - same text with different prefix?
			Concept sibling = findSibling(c);
			String[] siblingData = new String[] {"", "", "", ""};
			if (sibling != null) {
				addedForProcessing |= checkForProcessInclusion("Sibling", sibling, notes, action, c);
				reportedAsAdditional.add(sibling);
				siblingData[0] = sibling.toString();
				siblingData[1] = sibling.isActiveSafely()?"Y":"N";
				siblingData[2] = replacementMap.containsKey(sibling)?"Y":"N";
				siblingData[3] = SnomedUtils.prettyPrintHistoricalAssociations(sibling, gl, true);
			}

			Concept cousin = findCousin(c, true);
			String[] cousinData = new String[] {"", "", ""};
			if (cousin != null) {
				addedForProcessing |= checkForProcessInclusion("Cousin", cousin, notes, action, familyMember);
				cousinData[0] = cousin.toString();
				cousinData[1] = cousin.isActiveSafely()?"Y":"N";
				cousinData[2] = SnomedUtils.prettyPrintHistoricalAssociations(cousin, gl, true);
			}

			//Did we skip picking up the original concept, but then did find an action for the sibling or cousin?
			//Check it again if so.
			if (selfOriginallyNotIncluded && addedForProcessing) {
				action = null;
				if (replacementMap.containsKey(sibling)) {
					action = replacementMap.get(sibling);
					familyMember = sibling;
				} else if (replacementMap.containsKey(cousin)) {
					action = replacementMap.get(cousin);
					familyMember = cousin;
				}
				checkForProcessInclusion("Self", c, notes, action, familyMember);
			}

			//Did one of these concepts get added for processing
			if (!alreadyIncludedForProcessed || addedForProcessing) {
				String mismatchFlag = hasMismatchedAssociations(c, sibling, cousin) ? "Y" : "N";
				String notesStr = notes.stream().collect(Collectors.joining(",\n"));
				report(TERTIARY_REPORT, c, notesStr, mismatchFlag, assocStr, siblingData, cousinData);
			}
		}
	}

	private boolean shouldBeSkippedForRecentInactivationCheck(Concept c) {
		if (c.getFSNDescription() == null) {
			LOGGER.warn("Unpopulated {}", c.getId());
			return true;
		}
		
		return c.isActiveSafely()
				|| !inScope(c)
				|| 	manageManually.contains(c.getId())
				|| reportedAsAdditional.contains(c);
	}

	private boolean checkForProcessInclusion(String familyRelationship, Concept c, List<String> notes, UpdateAction action, Concept familyMember) {
		boolean addedForProcessing = false;
		//For 'NOS' concepts, we may pull them into our main processing set
		if (c.getFsn().contains("NOS")) {
			addedForProcessing |= checkForProcessInclusionNOS(familyRelationship, c, notes);
		} else if (c.getFsn().contains("context-dependent")) {
			addedForProcessing |= checkForProcessInclusionCDC(familyRelationship, c, notes, true);
		} else if (c.getFsn().contains("[")) {
			addedForProcessing |= checkForProcessInclusionCDC(familyRelationship, c, notes,false);
		} else {
			addedForProcessing |= checkForProcessInclusionMovedTo(familyRelationship, c, notes, action, familyMember);
			if (!addedForProcessing) {
				addedForProcessing |= checkForProcessInclusionAmbiguous(familyRelationship, c, notes);
			}
		}
		return addedForProcessing;
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

	private boolean checkForProcessInclusionNOS(String familyRelationship, Concept c, List<String> notes) {
		if (hasCommonReasonNotToInclude(familyRelationship, c, notes)) {
			return false;
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
			return false;
		}
		replacementMap.put(c, action);
		notes.add(familyRelationship + " included for processing - CDC");
		return true;
	}

	private boolean checkForProcessInclusionCDC(String familyRelationship, Concept c, List<String> notes, boolean allowMultipleTargets) {
		//The CDC here stands for Context Dependent Category
		if (hasCommonReasonNotToInclude(familyRelationship, c, notes)) {
			return false;
		}

		if (c.getInactivationIndicator() == null) {
			notes.add(familyRelationship + " not included for processing - check for missing inactivation indicator");
			return false;
		}

		if (!c.getInactivationIndicator().equals(InactivationIndicator.AMBIGUOUS)) {
			notes.add(familyRelationship + " not included for processing - inactivation indicator is not Ambiguous");
			return false;
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
				return false;
			}
		} else {
			notes.add(familyRelationship + " not included for processing due to lack of existing associations");
			return false;
		}
		replacementMap.put(c, action);
		notes.add(familyRelationship + " included for processing - NCEP");
		return true;
	}
	
	private boolean checkForProcessInclusionAmbiguous(String familyRelationship, Concept c, List<String> notes) {
		if (hasCommonReasonNotToInclude(familyRelationship, c, notes)) {
			return false;
		}

		if (c.getInactivationIndicator() ==  null) {
			notes.add(familyRelationship + " check original state. No inactivation indicator specified?");
		} else if (!c.getInactivationIndicator().equals(InactivationIndicator.AMBIGUOUS)) {
			return false;
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
			return false;
		}
		return true;
	}


	private boolean checkForProcessInclusionMovedTo(String familyRelationship, Concept c, List<String> notes, UpdateAction otherFamilyMemberAction, Concept familyMember) {
		if (c.getInactivationIndicator() != null &&
				c.getInactivationIndicator().equals(InactivationIndicator.MOVED_ELSEWHERE)) {
			//Did we have another family member that was given an action?  We can process if so
			if (otherFamilyMemberAction == null) {
				notes.add(familyRelationship + " not included for processing - moved elsewhere, but no sibling/cousin action specified");
				return false;
			} else if (otherFamilyMemberAction.associationTargetsUnchanged &&
						!c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_MOVED_TO_REFSETID, true).isEmpty()) {
				Set<Concept> assocTargets = familyMember.getAssociationEntries(ActiveState.ACTIVE, true).stream()
						.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
						.collect(Collectors.toSet());
				UpdateAction reworkedAction = new UpdateAction(otherFamilyMemberAction);
				reworkedAction.replacements = assocTargets;
				reworkedAction.associationTypeUnchanged = false;
				reworkedAction.associationTargetsUnchanged = false;
				reworkedAction.replacements = assocTargets;
				reworkedAction.type = Association.REPLACED_BY;
				replacementMap.put(c, reworkedAction);
				notes.add(familyRelationship + " included for processing - moved elsewhere reworked with sibling/cousin's association targets");
				return true;
			} else {
				replacementMap.put(c, otherFamilyMemberAction);
				notes.add(familyRelationship + " included for processing - moved elsewhere with sibling/cousin action");
				return true;
			}
		}
		return false;
	}


	private boolean hasCommonReasonNotToInclude(String familyRelationship, Concept c, List<String> notes) {
		if (c.isActiveSafely()) {
			notes.add(familyRelationship + " not included for processing - active");
			return true;
		}

		if (skipConcepts.contains(c)) {
			notes.add(familyRelationship + " not included for processing - in skip list");
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

	private List<String> getTargetFsns(String thisPrefix, String rootFsn) {
		List<String> targetFsns = new ArrayList<>();

		targetPrefixes.stream().filter(siblingPrefix -> !siblingPrefix.equals(thisPrefix)).forEach(siblingPrefix -> {
			String targetFsn = siblingPrefix + " " + rootFsn;
			targetFsn = targetFsn.replace("  ", " ").trim();
			targetFsns.add(targetFsn);
		});

		return targetFsns;
	}

	//A cousin is the same basic FSN, but with a _different_ prefix
	private Concept findSibling(Concept c) {
		String rootFsn = UNKNOWN;
		String thisPrefix = UNKNOWN;
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				rootFsn = c.getFsn().replace(targetPrefix, "").replace("- ", "").trim();
				rootFsn = SnomedUtilsBase.deconstructFSN(rootFsn)[0];
				thisPrefix = targetPrefix;
				break;
			}
		}

		//Check for all alternative prefixes
		//Pre-generate these so we're not doing that inside the loop with all concepts
		List<String> targetFSNs = getTargetFsns(thisPrefix, rootFsn);

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
	private Concept findCousin(Concept c, boolean includeActive) {
		String targetFsn = UNKNOWN;
		for (String targetPrefix : targetPrefixes) {
			if (c.getFsn().startsWith(targetPrefix)) {
				targetFsn = c.getFsn().replace(targetPrefix, "").replace("- ", "").trim();
				targetFsn = SnomedUtilsBase.deconstructFSN(targetFsn)[0];
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

	protected String getCathyNotes(Concept c) {
		if (cathyNotes.containsKey(c)) {
			return cathyNotes.get(c);
		}
		return "";
	}

	@Override
	protected boolean report(Concept c, Object...details) throws TermServerScriptException {
		return report(PRIMARY_REPORT, c, details, getCathyNotes(c));
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
		report(QUATERNARY_REPORT, c, conceptGroup, active, updated, SnomedUtils.prettyPrintHistoricalAssociations(c, gl, true), getCathyNotes(c));
		finalStateConceptsReported.add(c);
	}

	class UpdateAction {
		boolean associationTargetsUnchanged = false;
		boolean associationTypeUnchanged = false;
		Association type;
		InactivationIndicator inactivationIndicator;
		Set<Concept> replacements;
		
		public UpdateAction () {
		}

		public UpdateAction (UpdateAction cloneMe) {
			this.associationTargetsUnchanged = cloneMe.associationTargetsUnchanged;
			this.associationTypeUnchanged = cloneMe.associationTypeUnchanged;
			this.type = cloneMe.type;
			this.inactivationIndicator = cloneMe.inactivationIndicator;
			if (this.replacements == null) {
				this.replacements = null;
			} else {
				this.replacements = new HashSet<>(cloneMe.replacements);
			}
		}

		public String toString() {
			String replacementsStr = "UNCHANGED";
			if (this.replacements != null) {
				replacementsStr = this.replacements.stream()
						.map(Concept::toString)
						.collect(Collectors.joining(","));
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
