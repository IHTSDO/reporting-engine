package org.ihtsdo.termserver.scripting.reports.release;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.mrcm.MRCMAttributeDomain;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DialectChecker;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ihtsdo.termserver.scripting.util.UnacceptableCharacters.*;

/**
 * INFRA-2723 Detect various possible issues
 * 
 * https://docs.google.com/spreadsheets/d/1jrCR_VOZ6k7qBwDAhTqbt67iisbm_rThV7vt37lr_Rg/edit#gid=0
 
 For cut-n-paste list of issues checked:
 ISRS-391 Descriptions whose module id does not match that of the component
 ISRS-392 Stated Relationships whose module id does not match that of the component
 MAINT-224 Synonyms created as TextDefinitions new content only
 INFRA-2580, MAINT-342 Inactivated concepts without active PT or synonym – new instances only
 ATF-1550 Check that concept has only one semantic tag – new and released content
 ISRS-414 Descriptions which contain a non-breaking space
 ISRS-286 Ensure Parents in same module
 RP-128 Ensure concepts referenced in axioms are active
 Active concept parents should not belong to more than one top-level hierarchy – please check NEW and LEGACY content for issues
 RP-127 Disease specific rules
 RP-165 Text definition dialect rules
 RP-179 concepts using surgical approach must be surgical procedures
 RP-181 Combined body sites cannot be the target for finding/procedure sites
 RP-181 No new combined body sites
 RP-201 Check for space before or after brackets
 RP-180 Check for attribute types that should never appear in the same group
 RP-180 Check for subHierarchies that should not use specific attribute types
 RP-202 Check for MsWord style double hyphen "—" (as opposed to "-")
 RP-241 Check axioms for correct module.  In fact, existing code is sufficient here.
 MAINT-1295 Report concepts with no semantic tag where modified in current release
 RP-414 Add check for repeated word groups
 RP-397 Check for duplicated words, words often typed in reverse, and highlight possible contraction changes
 CDI-52 Update to run successfully against projects with concrete values
 RP-465 Add check for regime/theraphy semtag not under 243120004|Regimes and therapies (regime/therapy)|
 INFRA-6817 Check MRCM for term discrepancies
 RP-553 Add check for zero sized space
 RP-609 Check LangRefsetEntries point to descriptions with appropriate langCode
 RP-878 Check Interprets/Has Interpretation attributes not grouped with any other attribute
 */
public class ReleaseIssuesReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseIssuesReport.class);

	private static final String CANNOT_READ = "Cannot read ";
	private static final String FAILURE_WHILE_READING = "Failure while reading: ";
	private static final String LOADING = "Loading {} ...";

	private static final String FULL_STOP = ".";
	Set<String> stopWords = new HashSet<>();
	List<String> wordsOftenTypedInReverse = new ArrayList<>();
	List<String> wordsOftenTypedTwice = new ArrayList<>();

	private static final String URL_REGEX = "https?://\\S+\\b";
	
	//See https://regex101.com/r/CAlQjx/1/
	public static final String SCTID_FSN_REGEX = "(\\d{7,})(\\s+)?\\|(.+?)\\|";
	private Pattern sctidFsnPattern;
	private static final int MIN_TEXT_DEFN_LENGTH = 12;
	DescendantsCache cache;
	private Set<Concept> deprecatedHierarchies;
	private List<Concept> allActiveConceptsSorted;
	private Set<Concept> recentlyTouched;
	private List<String> prepositions;
	private List<String> prepositionExceptions;
	private List<String> repeatedWordExceptions;
	Map<String, Concept> semTagHierarchyMap = new HashMap<>();
	List<Concept> allConceptsSorted;
	
	public static final String SCTID_CF_MOD = "11000241103";   //Common French Module
	public static final String SCTID_CH_MOD = "2011000195101"; //Swiss Module

	private static final int MUT_IDX_ACTIVE = 0;
	private static final int MUT_IDX_MODULEID = 1;
	
	private List<String> expectedExtensionModules = null;

	private static final int MAX_DESC_LENGTH = 255;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "N");
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		TermServerScript.run(ReleaseIssuesReport.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release Validation
		this.ignoreInputFileForReportName = true;
		super.init(run);
		additionalReportColumns = "FSN, Semtag, Issue, Legacy, C/D/R Active, Detail";
		cache = gl.getDescendantsCache();

		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		getArchiveManager().setLoadOtherReferenceSets(true);
		gl.setRecordPreviousState(true);  //Needed to check for module jumpers

		inputFiles.add(0, new File("resources/prepositions.txt"));
		inputFiles.add(1, new File("resources/preposition-exceptions.txt"));
		inputFiles.add(2, new File("resources/repeated-word-exceptions.txt"));
		loadPrepositionsAndExceptions();
		loadRepeatedWordExceptions();

		stopWords.add("of");
		stopWords.add("Product");
		stopWords.add("the");
		stopWords.add("The");
		stopWords.add("mg");
		stopWords.add("left");
		stopWords.add("right");
		stopWords.add("and");
		stopWords.add("with");
		stopWords.add("bone");
		stopWords.add("from");
		stopWords.add("blood");
		stopWords.add("NOS]");
		stopWords.add("disorder");
		stopWords.add("disease");
		stopWords.add("[in");
		stopWords.add("cell");

		wordsOftenTypedInReverse.add("To");
		wordsOftenTypedInReverse.add("to");
		wordsOftenTypedInReverse.add("Of");
		wordsOftenTypedInReverse.add("of");

		wordsOftenTypedTwice.add("To");
		wordsOftenTypedTwice.add("to");
		wordsOftenTypedTwice.add("Of");
		wordsOftenTypedTwice.add("of");
		wordsOftenTypedTwice.add("About");
		wordsOftenTypedTwice.add("about");
		wordsOftenTypedTwice.add("Not");
		wordsOftenTypedTwice.add("not");
		wordsOftenTypedTwice.add("For");
		wordsOftenTypedTwice.add("for");
		wordsOftenTypedTwice.add("And");
		wordsOftenTypedTwice.add("and");
		wordsOftenTypedTwice.add("Or");
		wordsOftenTypedTwice.add("or");
		wordsOftenTypedTwice.add("With");
		wordsOftenTypedTwice.add("with");
		wordsOftenTypedTwice.add("Be");
		wordsOftenTypedTwice.add("be");
		
		sctidFsnPattern = Pattern.compile(SCTID_FSN_REGEX, Pattern.MULTILINE);
	}

	public void loadPrepositionsAndExceptions() throws TermServerScriptException {
		LOGGER.info(LOADING, getInputFile());
		if (!getInputFile().canRead()) {
			//Now it might be that the server is still starting up.  Let's give it 10 seconds and try again
			try {
				LOGGER.info("Sleeping for 10 seconds - has the server just started?");
				Thread.sleep(10000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			if (!getInputFile().canRead()) {
				throw new TermServerScriptException(CANNOT_READ + getInputFile());
			}
		}
		try {
			prepositions = Files.readLines(getInputFile(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException(FAILURE_WHILE_READING + getInputFile(), e);
		}

		LOGGER.info(LOADING, getInputFile(1));
		if (!getInputFile(1).canRead()) {
			throw new TermServerScriptException(CANNOT_READ + getInputFile(1));
		}
		try {
			prepositionExceptions = Files.readLines(getInputFile(1), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException(FAILURE_WHILE_READING + getInputFile(1), e);
		}
	}

	private void loadRepeatedWordExceptions() throws TermServerScriptException {
		LOGGER.info(LOADING, getInputFile(2));
		if (!getInputFile(1).canRead()) {
			throw new TermServerScriptException(CANNOT_READ + getInputFile(2));
		}
		try {
			repeatedWordExceptions = Files.readLines(getInputFile(2), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException(FAILURE_WHILE_READING + getInputFile(2), e);
		}
		LOGGER.info("Complete");
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"SCTID, FSN, Semtag, Issue, Legacy, C/D/R Active, Detail, Additional Detail, Further Detail",
				"Issue, Count"
		};

		String[] tabNames = new String[] {
				"Issues",
				"Summary"
		};
		
		super.postInit(tabNames, columnHeadings);
		deprecatedHierarchies = new HashSet<>();
		deprecatedHierarchies.add(gl.getConcept("116007004|Combined site (body structure)|"));
	
		if (isMS()) {
			String defaultModule = project.getMetadata().getDefaultModuleId();
			expectedExtensionModules = project.getMetadata().getExpectedExtensionModules();
			if (expectedExtensionModules == null) {
				report(null, "expectedExtensionModules metadata not populated, using defaultModuleId instead.");
				expectedExtensionModules = Collections.singletonList(defaultModule);
			}
		}
		
		semTagHierarchyMap.put("(regime/therapy)", gl.getConcept("243120004|Regimes and therapies (regime/therapy)|"));
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.add(UNPROMOTED_CHANGES_ONLY)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Release Issues Report")
				.withDescription("This report lists a range of potential issues identified in INFRA-2723. " + 
						"\nThe options that can be selected are:" +
						"\nUnpromoted: New changes on the branch that have not been promoted yet. Can be task or project." +
						"\nLegacy: This will include all issues, including old legacy ones - should NOT be combined with Unpromoted." +
						"\nNo box checked: Report will check all new (unversioned) issues, regardless of the branch they were authored on.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		LOGGER.info("Checking {} concepts...", gl.getAllConcepts().size());
		allConceptsSorted = SnomedUtils.sort(gl.getAllConcepts());
		allActiveConceptsSorted = allConceptsSorted.stream()
				.filter(Component::isActiveSafely)
				.toList();
		LOGGER.info("Sorted {} concepts", allConceptsSorted.size());
		LOGGER.info("Detecting recently touched concepts");
		populateRecentlyTouched();

		LOGGER.info("Checking: ");
		LOGGER.info("...modules are appropriate (~10 seconds)");
		parentsInSameModule();
		if (isMS()) {
			unexpectedComponentModulesMS();
			inappropriateModuleJumping();
		} else {
			unexpectedDescriptionModules();
			unexpectedRelationshipModules();
			unexpectedAxiomModules();
		}
		
		LOGGER.info("...description rules");
		maxLengthCheck();
		fullStopInSynonym();
		missingFSN_PT();
		unexpectedCharacters();
		spaceBracket();
		missingSemanticTag();
		semTagInCorrectHierarchy();
		repeatedWordGroups();
		reviewContractions();
		//Splitting every description into an array is expensive, so run checks for
		//words in reverse and consecutive prepositions in the same loop
		runTokenisedDescriptionChecks();
		multipleLangRef();
		multiplePTs();
		multipleFSNs();
		if (isMS()) {
			unexpectedLangCodeMS();
		} else {
			dueWithoutTo();
		}

		checkComponentsReferenceDependentModules();

		LOGGER.info("...duplicate semantic tags");
		duplicateSemanticTags();
		
		LOGGER.info("...parent hierarchies (~20 seconds)");
		parentsInSameTopLevelHierarchy();
		
		LOGGER.info("...axiom integrity");
		axiomIntegrity();
		noStatedRelationships();
		
		LOGGER.info("...Disease semantic tag rule");
		diseaseIntegrity();

		if (!isMS()) {
			LOGGER.info("...FSN dialect checks");
			descriptionDialectChecks();

			LOGGER.info("...Text definition dialect checks");
			textDefinitionDialectChecks();
		}
		
		LOGGER.info("...Nested brackets check");
		nestedBracketCheck();
		
		LOGGER.info("...Modelling rules check");
		validateAttributeDomainModellingRules();
		validateAttributeTypeValueModellingRules();
		validateInterpretsHasInterpretation();
		neverGroupTogether();
		domainMustNotUseType();
		
		LOGGER.info("...Deprecation rules");
		checkDeprecatedHierarchies();
		
		LOGGER.info("...MRCM validation");
		checkMRCMDomain();
		checkMRCMAttributeRanges();
		checkMRCMAttributeDomains();
		checkMRCMModuleScope();

		LOGGER.info("Checks complete, creating summary tag");
		populateSummaryTabAndTotal(SECONDARY_REPORT);
		
		LOGGER.info("Summary tab complete, all done.");
	}

	private void checkComponentsReferenceDependentModules() throws TermServerScriptException {
		String issueStr = "Component references a module that is not visible from its own module, according to the MDRS";
		LOGGER.info("Starting check of components referencing dependent modules");
		//We're going to go through every component and check that the concepts it references,
		//belong to a module that is visible from the module of the component
		for (Component c : gl.getAllComponents()) {
			if (!c.isActiveSafely() || !inScope(c)) {
				continue;
			}
			//What is the moduleId of this component?
			String moduleId = c.getModuleId();
			for (Component referencedComponent : c.getReferencedComponents(gl)) {
				//What is the module of the referenced component?
				String referencedModule = referencedComponent.getModuleId();
				if (!referencedModule.equals(moduleId) &&
					!gl.getMdrs().getDependencies(moduleId).contains(referencedModule)) {
					Concept owningConcept = gl.getComponentOwner(c.getId());
					String msg = "Component references component in module " + referencedModule +
							" which is not visible from its own module " + moduleId;
					reportAndIncrementSummary(owningConcept, isLegacySimple(c), issueStr, getLegacyIndicator(c), isActive(c, referencedComponent), msg, c.toString());
				}
			}
		}
		LOGGER.info("Finished check of components referencing dependent modules");
	}

	private void inappropriateModuleJumping() throws TermServerScriptException {
		String issueStr = "Component module jumped, otherwise unchanged.";
		String issueStr2 = "Component module jumped without parent";
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		LOGGER.info("Started inappropriateModuleJumping check");
		for (Concept concept : allConceptsSorted) {
			nextComponent:
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				//Did it change in the current delta?  Don't bother checking if not
				if (!c.hasPreviousStateDataRecorded()) {
					continue;
				}
				
				//We'll give inferred relationships the benefit of the doubt
				//They can be changed by extensions without changing the owning component
				if (c instanceof Relationship) {
					continue;
				}
				
				String[] previousState = c.getPreviousState();
				String[] currentState = c.getMutableFields();
				if (previousState.length != currentState.length) {
					throw new TermServerScriptException("Investigate: component's state has changed length! Now " + currentState.length + " vs previous" + previousState.length);
				}
				//We're not expecting any fields to be null, log a message if so
				if (nullCheck("Previous", previousState, c) || nullCheck("Current", currentState, c)) {
					continue;
				}

				//If the module has not changed, then we've nothing to worry about.
				if (previousState[MUT_IDX_MODULEID].equals(currentState[MUT_IDX_MODULEID])) {
					continue;
				}
				
				//Check what fields are different.  It's a problem if ONLY the moduleId has changed
				boolean differenceFound = false;
				for (int idx=0; idx < previousState.length; idx++) {
					if (idx == 1) {
						//If the module hasn't changed, no need to check fields any further
						if (previousState[idx].equals(currentState[idx])) {
							continue nextComponent;
						} else if (previousState[idx].equals("15561000146104") && currentState[idx].equals("11000146104")) {
							//All components from the NL Patient Friendly module have moved to the main NL module
							continue nextComponent;
						}
					} else if (!previousState[idx].equals(currentState[idx])) {
						differenceFound = true;
						break;
					}
				}
				
				if (!differenceFound) {
					String msg = c.getComponentType() + " previously: " + Arrays.toString(previousState) + " vs current " + Arrays.toString(c.getMutableFields());
					reportAndIncrementSummary(concept, isLegacySimple(c), issueStr, getLegacyIndicator(c), isActive(concept,c), msg, c, c.getId());
				} else {
					//Now even if there IS a difference, then we don't expect components to change
					//module without their parent object - concept or description
					Component owningObject = SnomedUtils.getParentComponent(c, gl);
					if (owningObject == null) {
						LOGGER.warn("Could not determine owner of {}", c);
					} else if (!hasChangedModule(owningObject)) {
						if (owningObject.getModuleId().equals(SCTID_CF_MOD) && 
								isExpectedModuleJumpException(c, previousState, currentState)) {
							continue;
						}

						String msg = c.getComponentType() + " previously: " + Arrays.toString(previousState) + " vs current " + Arrays.toString(c.getMutableFields());
						reportAndIncrementSummary(concept, isLegacySimple(c), issueStr2, getLegacyIndicator(c), isActive(concept,c), msg, c, c.getId());
					}
				}
			}
		}
		LOGGER.info("Completed inappropriateModuleJumping check");
	}

	private boolean nullCheck(String view, String[] viewState, Component c) {
		for (int i=0; i < viewState.length; i++) {
			if (viewState[i] == null) {
				LOGGER.error("Null value at idx {} in {} state of {}", i, view, c);
				return true;
			}
		}
		return false;
	}

	private boolean isExpectedModuleJumpException(Component c, String[] previousState, String[] currentState) {
		String prevModule = previousState[MUT_IDX_MODULEID];
		String currModule = currentState[MUT_IDX_MODULEID];
		String prevActive = previousState[MUT_IDX_ACTIVE];
		
		//RP-675 Add allowance for CF LRS entries on CF descriptions being inactivated in CH Module
		if (c instanceof LangRefsetEntry &&
				prevModule.equals(SCTID_CF_MOD) &&
				currModule.equals(SCTID_CH_MOD) &&
				!c.isActiveSafely() && prevActive.equals("1")) {
			return true;
		}
		return false;
	}

	private boolean hasChangedModule(Component c) throws TermServerScriptException {
		//If the component has an effective time, then it hasn't changed in this release
		if (!StringUtils.isEmpty(c.getEffectiveTime())) {
			return false;
		}
		String[] previousState = c.getPreviousState();
		String[] currentState = c.getMutableFields();
		if (previousState.length != currentState.length) {
			throw new TermServerScriptException("Investigate: component's state has changed length! Previous state: '" + c.getIssues() + "' vs current: " + c);
		}
		
		String prevModule = previousState[MUT_IDX_MODULEID];
		String currModule = currentState[MUT_IDX_MODULEID];
		return prevModule.equals(currModule);
	}

	//ISRS-286 Ensure Parents in same module.
	//This check does not apply to MS
	private void parentsInSameModule() throws TermServerScriptException {
		if (isMS()) {
			return;
		}
		
		String issueStr = "Mismatching parent moduleId";
		initialiseSummary(issueStr);
		for (Concept c : allActiveConceptsSorted) {
			if (c.getModuleId() == null) {
				LOGGER.warn("Encountered concept with no module defined: {}", c);
				continue;
			}
			if (!c.getModuleId().equals(SCTID_CORE_MODULE) && !c.getModuleId().equals(SCTID_MODEL_MODULE)) {
				continue;
			}
			
			//Also skip the top of the metadata hierarchy - it has a core parent
			//900000000000441003 |SNOMED CT Model Component (metadata)|
			if (!c.isActiveSafely() || c.getConceptId().equals("900000000000441003")) {
				continue;
			}
			
			for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
				if (!p.getModuleId().equals(c.getModuleId())) {
					reportAndIncrementSummary(c, isLegacySimple(c), issueStr,getLegacyIndicator(c), isActive(c,null), p);
				}
			}
		}
	}

	//ISRS-391 Descriptions whose module id does not match that of the component
	//It's OK to add translations to core concepts, so does not apply to MS
	private void unexpectedDescriptionModules() throws TermServerScriptException {
		String issueStr ="Unexpected Description Module";
		initialiseSummary(issueStr);
		for (Concept c : allConceptsSorted) {
			for (Description d : c.getDescriptions()) {
				if (!d.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Desc module " + d.getModuleId();
					reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c,d), msg, d);
				}
			}
		}
	}
	
	/* Since and extension is based on a release, any modified description should
	 * belong to the default module
	 */
	private void unexpectedComponentModulesMS() throws TermServerScriptException {
		String issueStr ="Unexpected module for modified component";
		initialiseSummary(issueStr);
		LOGGER.info("Checking {} for unexpected component modules in modified components", allConceptsSorted.size());
		for (Concept c : allConceptsSorted) {
			for (Component comp: SnomedUtils.getAllComponents(c)) {
				if (StringUtils.isEmpty(comp.getEffectiveTime()) && !expectedExtensionModules.contains(comp.getModuleId())) {
					String msg = "Modified component module " + comp.getModuleId() + " is not expected in this extension";
					reportAndIncrementSummary(c, isLegacySimple(comp), issueStr, getLegacyIndicator(comp), isActive(c,comp), msg, comp);
				}
			}
		}
	}
	
	//ISRS-392 Part II Stated Relationships whose module id does not match that of the component
	private void unexpectedRelationshipModules() throws TermServerScriptException {
		String issueStr = "Unexpected Inf Rel Module";
		initialiseSummary(issueStr);
		for (Concept c : allConceptsSorted) {
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!r.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Rel module " + r.getModuleId();
					reportAndIncrementSummary(c, isLegacySimple(r), issueStr, getLegacyIndicator(r), isActive(c,r), msg, r);
				}
			}
		}
	}
	
	private void unexpectedAxiomModules() throws TermServerScriptException {
		String issueStr = "Unexpected Axiom Module";
		initialiseSummary(issueStr);
		for (Concept c : allConceptsSorted) {
			for (AxiomEntry a : c.getAxiomEntries()) {
				if (!a.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Axiom module " + a.getModuleId();
					reportAndIncrementSummary(c, isLegacySimple(a),issueStr, getLegacyIndicator(a), isActive(c,a), msg, a);
				}
			}
		}
	}
	
	//MAINT-224 Synonyms created as TextDefinitions new content only
	private void fullStopInSynonym() throws TermServerScriptException {
		String issueStr = "Possible TextDefn as Synonym";
		String issue2Str = ">1 Text Definition per Dialect";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		for (Concept c : allConceptsSorted) {
			if (whiteListedConceptIds.contains(c.getId())) {
				continue;
			}
			//Only look at concepts that have been in some way edited in this release cycle
			//Unless we're interested in legacy issues
			if (c.isActiveSafely() && (includeLegacyIssues || SnomedUtils.hasNewChanges(c))) {
				for (Description d : c.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
					if (inScope(d)) {
						if (d.getTerm().endsWith(FULL_STOP) && d.getTerm().length() > MIN_TEXT_DEFN_LENGTH) {
							reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c,d), d);
						}
					}
				}
				
				if (inScope(c)) {
					//Check we've only got max 1 Text Defn for each dialect
					if (c.getDescriptions(US_ENG_LANG_REFSET, Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE).size() > 1 ||
						c.getDescriptions(GB_ENG_LANG_REFSET, Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE).size() > 1 ) {
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issue2Str, "N", "Y");
					}
				}
			}
		}
	}

	private void maxLengthCheck() throws TermServerScriptException {
		String issueStr = "Description (not TextDefn) exceeds " + MAX_DESC_LENGTH + " characters";
		initialiseSummary(issueStr);
		for (Concept c : allConceptsSorted) {
			if (whiteListedConceptIds.contains(c.getId())) {
				continue;
			}
			//Only look at concepts that have been in some way edited in this release cycle
			//Unless we're interested in legacy issues
			if (c.isActiveSafely() && (includeLegacyIssues || SnomedUtils.hasNewChanges(c))) {
				checkDescriptionsForExcessiveLength(c, issueStr);
			}
		}
	}

	private void checkDescriptionsForExcessiveLength(Concept c, String issueStr) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				continue;
			}

			if (inScope(d) && d.getTerm().length() > MAX_DESC_LENGTH) {
				reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c,d), d);
			}
		}
	}

	//INFRA-2580, MAINT-342 Inactivated concepts without active PT or synonym – new instances only
	//RP-478 Broaden to take in all concepts - just in case!
	private void missingFSN_PT() throws TermServerScriptException {
		String issueStr = "Concept without active FSN";
		String issue2Str = "Concept without active US PT";
		String issue3Str = "Concept without active GB PT";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		initialiseSummary(issue3Str);
		for (Concept c : allConceptsSorted) {
			if (inScope(c) && isInternational(c) && (includeLegacyIssues || recentlyTouched.contains(c))) {
				if (c.getFSNDescription() == null || !c.getFSNDescription().isActiveSafely()) {
					reportAndIncrementSummary(c, isLegacySimple(c), issueStr, getLegacyIndicator(c), isActive(c,null));
				}
				
				Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
				if (usPT == null || !usPT.isActiveSafely()) {
					reportAndIncrementSummary(c, isLegacySimple(c), issue2Str, getLegacyIndicator(c), isActive(c,null));
				}
				
				Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
				if (gbPT == null || !gbPT.isActiveSafely()) {
					reportAndIncrementSummary(c, isLegacySimple(c), issue3Str, getLegacyIndicator(c), isActive(c,null));
				}
			}
		}
	}
	
	private void missingSemanticTag() throws TermServerScriptException {
		String issueStr = "Concept (recently touched) with invalid FSN";
		initialiseSummary(issueStr);
		for (Concept c : allConceptsSorted) {
			if (inScope(c)
				&& recentlyTouched.contains(c)
				&& c.getFsn() != null
				&& SnomedUtilsBase.deconstructFSN(c.getFsn(), includeLegacyIssues)[1] == null) {
				reportAndIncrementSummary(c, false, issueStr, "N", isActive(c,c.getFSNDescription()), c.getFsn());
			}
		}
	}
	
	private void semTagInCorrectHierarchy() throws TermServerScriptException {
		String issueStr = "SemTag used outside of expected hierarchy";
		initialiseSummary(issueStr);
		for (Concept c : allActiveConceptsSorted) {
			if (inScope(c) && !semTagHierarchyMap.containsValue(c)) {
				for (Map.Entry<String, Concept> entry : semTagHierarchyMap.entrySet()) {
					String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn(), true)[1];
					//Are we in the appropriate Hierarchy?
					if (semTag != null && semTag.equals(entry.getKey()) && !c.getAncestors(NOT_SET).contains(entry.getValue())) {
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, "-", isActive(c,c.getFSNDescription()), entry.getValue());
					}
				}
			}
		}
	}

	private void repeatedWordGroups() throws TermServerScriptException {
		final Collection<Concept> concepts = includeLegacyIssues ? allConceptsSorted : allActiveConceptsSorted;
		nextConcept:
		for (Concept c : concepts) {
			if (inScope(c) && (includeLegacyIssues || recentlyTouched.contains(c))) {
				//We're going to skip concepts with clinical drugs
				String fsn = c.getFsnSafely();
				if (fsn.contains("(medicinal") ||
						fsn.contains("(clinical") ||
						fsn.contains("(product")) {
					continue nextConcept;
				}


				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean includeLegacyIssuesOrNonReleasedIssues = includeLegacyIssues ? true : !d.isReleased();
					if (includeLegacyIssuesOrNonReleasedIssues && repeatedWordGroups(c, d, new ConcernLevel())) {
						//No need to report more than once per concept
						break;
					}
				}
			}
		}
	}

	private boolean repeatedWordGroups(Concept c, Description d, ConcernLevel concern) throws TermServerScriptException {
		//If this is a text definition, that's less concerning
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION) || !d.isActiveSafely() || !c.isActiveSafely()) {
			//Turning down the sensitivity here, lots of words makes for more repetition.
			concern.decrease(2);
		}

		String[] words = d.getTerm().split(" ");
		for (int x = 0; x < words.length; x++) {
			if (stopWords.contains(words[x]) ||
					repeatedWordExceptions.contains(words[x]) ||
					words[x].length() <= 2 ) {
				continue;
			}

			if (checkForSideBySideRepeats(x, c, d, words)) {
				return true;
			}

			//Second loop to check every word (x) against every other word (y)
			//Word 1 will already have been tested against eg word 5, so no need to test word 5 against word 1.
			//Therefore, start y wherever x is.  Plus we don't need to test the same word against itself, so plus 1.
			for (int y = x + 1; y < words.length; y++) {
				 if (checkForDuplicatedWordGroups(x, y, c, d, words, concern)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean checkForSideBySideRepeats(int x, Concept c, Description d, String[] words) throws TermServerScriptException {
		String wordIssueStr = "Description uses repeating words";
		initialiseSummary(wordIssueStr);
		//Check for duplicate words that are side-by-side.
		if (x + 1 < words.length) {
			String currentWord = words[x];
			String nextWord = words[x + 1];
			boolean wordsEqual = currentWord.equalsIgnoreCase(nextWord);
			boolean wordOftenTypedTwice = wordsOftenTypedTwice.contains(currentWord);
			if (wordsEqual && wordOftenTypedTwice) {
				reportAndIncrementSummary(c, isLegacySimple(d), wordIssueStr, getLegacyIndicator(d), isActive(c, d), "Repeated word: " + currentWord, d);
				return true;
			}
		}
		return false;
	}

	private boolean checkForDuplicatedWordGroups(int x, int y, Concept c, Description d, String[] words, ConcernLevel concern) throws TermServerScriptException {
		String wordGroupIssueStr = "Description uses repeating word group";
		initialiseSummary(wordGroupIssueStr);

		//Check for duplicated word groups.
		if (x != y && words[x].equalsIgnoreCase(words[y])) {
			concern.increment();
			//If we have an 'and' or 'width' to the left that we haven't counted, that's
			//less of a concern
			if (compoundToTheLeftOf(words, x)) {
				concern.decrement();
			}

			//We'll also check a word left or right
			//of X to be the same as a word to the left or right of Y
			if (!alsoHasSameWordToLeftOrRight(words, x, y)) {
				concern.decrement();
			}

			if (concern.isConcerning(2)) {
				reportAndIncrementSummary(c, isLegacySimple(d), wordGroupIssueStr, getLegacyIndicator(d), isActive(c, d), "Repeated word: " + words[x], d);
				return true;
			}
		}
		return false;
	}


	private void reviewContractions() throws TermServerScriptException {
		String issueStr = "Contraction(s) to be reviewed for Concept";
		String detailStr = "Option to add/remove contraction(s)";
		initialiseSummary(issueStr);

		nextConcept:
		for (Concept c : allActiveConceptsSorted) {
			if (inScope(c) && (includeLegacyIssues || recentlyTouched.contains(c))) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					String[] words = d.getTerm().split(" ");
					int wordsLength = words.length;
					for (int x = 0; x < wordsLength; x++) {
						String currentWord = words[x];
						if ((unpromotedChangesOnly && unpromotedChangesHelper.hasUnpromotedChange(c) && "cannot".equalsIgnoreCase(currentWord)) ||
								"can't".equalsIgnoreCase(currentWord) ||
								(x + 1 < wordsLength && "can".equalsIgnoreCase(currentWord) && "not".equalsIgnoreCase(words[x + 1]))) {
							reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), detailStr, d);
							continue nextConcept;
						}
					}
				}
			}
		}
	}

	private void runTokenisedDescriptionChecks() throws TermServerScriptException {
		String issueStr = "Potential mistyped word in Description";
		initialiseSummary(issueStr);
		String issueStr2 = "Consecutive prepositions detected";
		initialiseSummary(issueStr2);

		for (Concept c : allActiveConceptsSorted) {
			if (inScope(c) && (includeLegacyIssues || recentlyTouched.contains(c))) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					String[] words = d.getTerm().split(" ");
					checkForReversedWords(c, d, issueStr, words);
					checkForConsecutivePrepositions(c, d, issueStr2, words);
				}
			}
		}
	}

	private void checkForConsecutivePrepositions(Concept c, Description d, String issueStr2, String[] words) throws TermServerScriptException {
		for (int x = 0; x < words.length; x++) {
			String currentWord = words[x].toLowerCase();
			if (prepositions.contains(currentWord)) {
				if (x + 1 < words.length) {
					String nextWord = words[x + 1].toLowerCase();
					if (prepositions.contains(nextWord)) {
						//Check for this being an allowable exception combination
						if (prepositionExceptions.contains(currentWord + " " + nextWord)) {
							continue;
						}
						reportAndIncrementSummary(c, isLegacySimple(d), issueStr2, getLegacyIndicator(d), isActive(c, d), "Consecutive prepositions: " + currentWord + " " + nextWord, d);
					}
				}
			}
		}
	}

	private void checkForReversedWords(Concept c, Description d, String issueStr, String[] words) throws TermServerScriptException {
		for (String currentWord : words) {
			String currentWordInReverse = new StringBuilder(currentWord).reverse().toString();
			int indexOf = wordsOftenTypedInReverse.indexOf(currentWordInReverse);
			if (indexOf != -1) {
				String detailStr = String.format("The word '%s' looks to be '%s' in reverse.", currentWord, wordsOftenTypedInReverse.get(indexOf));
				reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), detailStr, d);
			}
		}
	}
	
	private void multipleLangRef() throws TermServerScriptException {
		String issueStr = "Multiple LangRef for a given refset";
		initialiseSummary(issueStr);
		for (Concept c : allConceptsSorted) {
			if (inScope(c)) {
				nextDescription:
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					Set<String> activeInRefsets = new HashSet<>();
					for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
						if (activeInRefsets.contains(l.getRefsetId())) {
							String detailStr = "Description has multiple langrefset entries in same refset: " + l.getRefsetId();
							reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), detailStr, d);
							continue nextDescription;
						} else {
							activeInRefsets.add(l.getRefsetId());
						}
					}
				}
			}
		}
	}
	
	private void unexpectedLangCodeMS() throws TermServerScriptException {
		//We need a branch to be able to run this query
		if (getArchiveManager().isLoadDependencyPlusExtensionArchive()) {
			LOGGER.info("Unable to determine appropriate langCode for LangRefsets when working with archive package");
			return;
		}
		String issueStr = "Langrefset's description has unexpected langCode";
		initialiseSummary(issueStr);
		Map<String, String> refsetLangCodeMap = generateRefsetLangCodeMap();
		for (Concept c : allConceptsSorted) {
			checkUnexpectedLangCode(c, issueStr, refsetLangCodeMap);
		}
	}

	private void checkUnexpectedLangCode(Concept c, String issueStr, Map<String, String> refsetLangCodeMap) throws TermServerScriptException {
		for (Description d : c.getDescriptions()) {
			//It's OK - for example - to have an English term in the Dutch LangRefset
			//So skip 'en' terms, unless it's the FSN
			if (d.getType().equals(DescriptionType.FSN) || !d.getLang().equals("en")) {
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
					String expectedLangCode = refsetLangCodeMap.get(l.getRefsetId());
					if (expectedLangCode == null) {
						throw new TermServerScriptException("Unable to determine appropriate langCode for Langrefset: " + gl.getConcept(l.getRefsetId()));
					}
					if (!d.getLang().equals(expectedLangCode)) {
						String detailStr = "Expected '" + expectedLangCode + "'";
						reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), detailStr, d, l);
					}
				}
			}
		}
	}

	private Map<String, String> generateRefsetLangCodeMap() {
		Map<String, String> refsetLangCodeMap = new HashMap<>();
		//First populate en-gb and en-us since we always know about those
		refsetLangCodeMap.put(US_ENG_LANG_REFSET, "en");
		refsetLangCodeMap.put(GB_ENG_LANG_REFSET, "en");
		
		//Now the optionalLanguageRefsets are laid out nicely
		Metadata metadata = project.getMetadata();
		refsetLangCodeMap.putAll(metadata.getLangRefsetLangMapping());
		return refsetLangCodeMap;
	}

	private void multiplePTs() throws TermServerScriptException {
		String issueStr = "Multiple preferred synonyms in a single langrefset";
		initialiseSummary(issueStr);
		List<DescriptionType> typesOfInterest = Collections.singletonList(DescriptionType.SYNONYM);
		Map<String, Description> ptMap = new HashMap<>();
		for (Concept c : allConceptsSorted) {
			ptMap.clear();
			nextDescription:
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, typesOfInterest)) {
				boolean inScopePTDetected = false;
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
					if (l.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
						if (inScope(d)) {
							inScopePTDetected = true;
						}
						if (ptMap.containsKey(l.getRefsetId())) {
							if (inScopePTDetected) {
								String detailStr = d + " + " + ptMap.get(l.getRefsetId());
								reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), detailStr, d);
								continue nextDescription;
							}
						} else {
							ptMap.put(l.getRefsetId(), d);
						}
					}
				}
			}
		}
	}
	
	private void multipleFSNs() throws TermServerScriptException {
		String issueStr = "Multiple active FSNs in same language";
		initialiseSummary(issueStr);
		List<DescriptionType> typesOfInterest = Collections.singletonList(DescriptionType.FSN);
		Map<String, Description> fsnMap = new HashMap<>();
		for (Concept c : allConceptsSorted) {
			fsnMap.clear();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, typesOfInterest)) {
				boolean inScopeFSNDetected = false;
				if (inScope(d)) {
					inScopeFSNDetected = true;
				}
				if (fsnMap.containsKey(d.getLang())) {
					if (inScopeFSNDetected) {
						String detailStr = d + ",\n" + fsnMap.get(d.getLang());
						reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), detailStr);
					}
				} else {
					fsnMap.put(d.getLang(), d);
				}
			}
		}
	}
	
	private void dueWithoutTo() throws TermServerScriptException {
		String issueStr = "'Due' not followed by 'to'";
		String[] acceptableAlternativesPost = new String[] { "date", "mostly to", "either to", "with", "next", "new"};
		String[] acceptableAlternativesPre = new String[] { "claim" };
		initialiseSummary(issueStr);
		for (Concept c : allActiveConceptsSorted) {
			nextDescription:
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().contains("due") || d.getTerm().contains("Due")) {
					String term = " " + d.getTerm().toLowerCase() + " ";
					if (d.getType().equals(DescriptionType.FSN)) {
						term = " " + SnomedUtilsBase.deconstructFSN(d.getTerm())[0].toLowerCase() + " ";
					}
					//Might have more than one Due to find
					int idx = term.indexOf(" due ");
					while (idx != NOT_FOUND) {
						boolean acceptableAltFound = false;
						//Due at the end of the term if fine
						if (term.endsWith(" due ")) {
							continue nextDescription;
						}
						//Is this one of our acceptable alternatives
						for (String acceptableAlt : acceptableAlternativesPost) {
							if (term.indexOf(" due " + acceptableAlt, idx) == idx) {
								acceptableAltFound = true;
								break;
							}
						}
						
						for (String acceptableAlt : acceptableAlternativesPre) {
							if (term.contains(acceptableAlt + " due")) {
								continue nextDescription;
							}
						}
						
						if (!acceptableAltFound && term.indexOf(" due to", idx) == NOT_FOUND) {
							reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c, d), d);
							continue nextDescription;
						}
						idx = term.indexOf(" due ", idx + 1);
					}
				}
			}
		}
	}
	
	private boolean alsoHasSameWordToLeftOrRight(String[] words, int x, int y) {
		//So we're looking to see if a word left of X is the same as a word to the left of Y
		if (x > 0 && y > 0 && words[x-1].equalsIgnoreCase(words[y-1])) {
			return true;
		}
		
		if (x+1 < words.length && y+1 < words.length && words[x+1].equalsIgnoreCase(words[y+1])) {
			return true;
		}
		
		return false;
	}

	private boolean compoundToTheLeftOf(String[] words, int x) {
		//Is there a 'and' or 'width' to the left of x?
		for (int y=0; y < x ; y++) {
			if (words[y].equalsIgnoreCase("and") || 
					words[y].equalsIgnoreCase("with") || 
					words[y].equalsIgnoreCase("of") || 
					words[y].equalsIgnoreCase("to")) {
				return true;
			}
		}
		return false;
	}

	private void populateRecentlyTouched() {
		if (recentlyTouched == null) {
			recentlyTouched = SnomedUtils.getRecentlyTouchedConcepts(allConceptsSorted);
		}
	}

	private boolean isInternational(Concept c) {
		return c.getModuleId().equals(SCTID_CORE_MODULE) || c.getModuleId().equals(SCTID_MODEL_MODULE);
	}

	//ATF-1550 Check that concept has only one semantic tag – new and released content
	private void duplicateSemanticTags() throws TermServerScriptException {
		String issueStr = "FSN missing semantic tag";
		String issue2Str = "Multiple semantic tags";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		Map<String, Concept> knownSemanticTags = new HashMap<>();
		Set<String> whiteList = new HashSet<>();
		whiteList.add("368847001");
		whiteList.add("368812009");
		whiteList.add("385238005");
		whiteList.add("368808003");
		
		//First pass through all active concepts to find semantic tags
		for (Concept c : allActiveConceptsSorted) {
			if (c.getFSNDescription() == null) {
				LOGGER.warn("No FSN Description found for concept {}", c.getConceptId());
				continue;
			}
			if (c.isActiveSafely()) {
				if (!inScope(c)) {
					continue;
				}
				String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
				if (StringUtils.isEmpty(semTag)) {
					String legacy = getLegacyIndicator(c.getFSNDescription());
					reportAndIncrementSummary(c, isLegacySimple(c.getFSNDescription()), issueStr, legacy, isActive(c,c.getFSNDescription()), c.getFsn());
				} else {
					knownSemanticTags.put(semTag, c);
				}
			}
		}
		
		LOGGER.info("Collected {} distinct semantic tags", knownSemanticTags.size());
		
		//Second pass to see if we have any of these remaining once
		//the real semantic tag (last set of brackets) has been removed
		for (Concept c : allActiveConceptsSorted) {
			if (!inScope(c)) {
				continue;
			}
			if (whiteList.contains(c.getId()) || whiteListedConceptIds.contains(c.getId())) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}

			if (c.getFSNDescription() == null) {
				LOGGER.warn("No FSN Description found (2nd pass) for concept {}", c.getConceptId());
				continue;
			}
			String legacy = getLegacyIndicator(c.getFSNDescription());
			
			//Don't log lack of semantic tag for inactive concepts
			String termWithoutTag = SnomedUtilsBase.deconstructFSN(c.getFsn(), !c.isActiveSafely())[0];
			
			//We can shortcut this if we don't have any brackets here.
			if (!termWithoutTag.contains("(")) {
				continue;
			}
			for (Map.Entry<String, Concept> entry : knownSemanticTags.entrySet()) {
				if (termWithoutTag.contains(entry.getKey())) {
					reportAndIncrementSummary(c, "Y".equals(legacy), issue2Str, legacy, isActive(c,c.getFSNDescription()), c.getFsn(), "Contains semtag: " + entry.getKey() + " identified by " + entry.getValue());
				}
			}
		}
	}
	
	//ISRS-414 Descriptions which contain a non-breaking space
	private void unexpectedCharacters () throws TermServerScriptException {
		String [][] unwantedChars = new String[][] {
			{ ZEROSP , "Zero-sized space" },
			{ NBSPSTR , "Non-breaking space" },
			{ LONG_DASH , "MsWord style dash" },
			{ EN_DASH , "EN dash" },
			{ EM_DASH , "EM dash" },
			{ "--" , "Double dash" },
			{ RIGHT_APOS , "Right apostrophe" },
			{ LEFT_APOS , "Left apostrophe" },
			{ RIGHT_QUOTE , "Right quote" },
			{ LEFT_QUOTE , "Left quote" },
			{ GRAVE_ACCENT , "Grave accent" },
			{ ACUTE_ACCENT , "Acute accent" },
			{ SOFT_HYPHEN , "Soft hyphen" }
		};
		
		for (String[] unwantedChar : unwantedChars) {
			String issueStr = "Unexpected character(s) - " + unwantedChar[1];
			initialiseSummary(issueStr);
			
			for (Concept c : allActiveConceptsSorted) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (inScope(d)) {
						if (d.getTerm().indexOf(unwantedChar[0]) != NOT_SET && !allowableException(c, unwantedChar[0], d.getTerm())) {
							String legacy = getLegacyIndicator(d);
							String msg = "At position: " + d.getTerm().indexOf(unwantedChar[0]);
							reportAndIncrementSummary(c, "Y".equals(legacy), issueStr, legacy, isActive(c,d),msg, d);
							//Only report the first violation for each concept
							break; //Or if we didn't report one due to being promoted, we're not going to report the others
						}
					}
				}
			}
		}
	}

	private boolean allowableException(Concept c, String unwantedChars, String term) {
		//Only exceptions just now are for double dashes
		if (unwantedChars.equals("--")) {
			//See RP-202 for specification of exceptions
			String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
			if (semTag.equals("(organism)")) {
				//All double dashes in organism are allowed
				return true;
			} else if (semTag.equals("(substance)")) {
				if (term.contains("-->")) {
					return false;
				} else {
					return true;
				}
			}
		}
		return false;
	}

	//RP-201
	private void spaceBracket() throws TermServerScriptException {
		String issueStr = "Extraneous space inside bracket";
		initialiseSummary(issueStr);
		nextConcept:
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely() || includeLegacyIssues) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (inScope(d)) {
						if (d.getTerm().contains("( ") || d.getTerm().contains(" )")) {
							reportAndIncrementSummary(c, isLegacySimple(d), issueStr, getLegacyIndicator(d), isActive(c,d), d);
							continue nextConcept;
						}
					}
				}
			}
		}
	}
	
	//Active concept parents should not belong to more than one top-level hierarchy – please check NEW and LEGACY content for issues
	private void parentsInSameTopLevelHierarchy() throws TermServerScriptException {
		String issueStr = "Parent has multiple top level ancestors";
		String issue2Str = "Mixed TopLevel Parents";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		Set<Concept> whiteList = new HashSet<>();
		whiteList.add(gl.getConcept("411115002 |Drug-device combination product (product)|")); 
				
		nextConcept:
		for (Concept c : allActiveConceptsSorted) {
			if (!inScope(c)) {
				continue;
			}
			if (whiteListedConceptIds.contains(c.getId())) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			if (c.isActiveSafely()) {
				String legacy = getLegacyIndicator(c);
				
				//Skip root concept - has no highest ancestor
				if (c.equals(ROOT_CONCEPT)) {
					continue;
				}
				
				//If this concept - or any of its ancestors - are whitelisted, then skip
				for (Concept a : gl.getAncestorsCache().getAncestorsOrSelf(c)){
					if (whiteList.contains(a)) {
						continue nextConcept;
					}
				}
				
				Concept lastTopLevel = null;
				for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
					//If we are a top level, skip also
					if (p.equals(ROOT_CONCEPT)) {
						continue nextConcept;
					}
					//What top level hierarchy is this parent in?
					Set<Concept> topLevels = SnomedUtils.getHighestAncestorsBefore(p, ROOT_CONCEPT);
					
					if (topLevels.size() > 1) {
						String topLevelStr = topLevels.stream().map(Object::toString).collect(Collectors.joining(",\n"));
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c,null), topLevelStr);
						continue nextConcept;
					} else if (topLevels.isEmpty()) {
						reportAndIncrementSummary(c, false, "Failed to find top level of parent ", legacy, isActive(c,null), p);
						continue nextConcept;
					}
					
					Concept thisTopLevel = topLevels.iterator().next();
					if (lastTopLevel == null) {
						lastTopLevel = thisTopLevel;
					} else if ( !lastTopLevel.equals(thisTopLevel)) {
						reportAndIncrementSummary(c, "Y".equals(legacy), issue2Str, legacy, isActive(c,null), thisTopLevel, lastTopLevel);
					}
				}
			}
		}
	}
	
	//RP-128
	private void axiomIntegrity() throws TermServerScriptException {
		String issueStr = "Axiom contains inactive type";
		String issue2Str = "Axiom contains inactive target";
		String issue3Str = "GCI Axiom contains inactive type";
		String issue4Str = "GCI Axiom contains inactive type";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		initialiseSummary(issue3Str);
		initialiseSummary(issue4Str);
		
		//Check all concepts referenced in relationships are valid
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely() && inScope(c)) {
				//Check all RHS relationships are active
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					String legacy = getLegacyIndicator(r);
					if (!r.getType().isActiveSafely()) {
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c,r), r);
					}
					if (r.isNotConcrete() && !r.getTarget().isActiveSafely()) {
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issue2Str, legacy, isActive(c,r), r);
					}
				}
				
				//Check all LHS relationships are active
				for (AxiomEntry a : c.getAxiomEntries(ActiveState.ACTIVE, true)) {
					try {
						String legacy = getLegacyIndicator(a);
						AxiomRepresentation axiom = gl.getAxiomService().convertAxiomToRelationships(a.getOwlExpression());
						//Things like property chains give us a null axiom
						if (axiom == null) {
							continue;
						}
						
						for (Relationship r : AxiomUtils.getLHSRelationships(c, axiom)) {
							if (!r.getType().isActiveSafely()) {
								reportAndIncrementSummary(c, !recentlyTouched.contains(c), issue3Str, legacy, isActive(c,r), r);
							}
							if (r.isNotConcrete() && !r.getTarget().isActiveSafely()) {
								reportAndIncrementSummary(c, !recentlyTouched.contains(c), issue4Str, legacy, isActive(c,r), r);
							}
						}
					} catch (ConversionException e) {
						LOGGER.error ("Failed to convert: " + a, e);
					}
				}
			}
		}
	}
	
	/**
	 * This will not spot many stated relationships because the axiom equivalents
	 * will override these rows.
	 * @throws TermServerScriptException
	 */
	private void noStatedRelationships() throws TermServerScriptException {
		String issueStr = "Active stated relationship";
		initialiseSummary(issueStr);
		
		//Check no active relationship is non-axiom
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely() && inScope(c)) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					String legacy = getLegacyIndicator(r);
					if (!r.fromAxiom()) {
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c,r), r);
					}
				}
			}
		}
	}
	
	//RP-127
	private void diseaseIntegrity() throws TermServerScriptException {
		String issueStr = "Clinical finding has disorder as ancestor ";
		String issue2Str = "Disorder is not descendant of 64572001|Disease (disorder)| ";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		//Rule 1 (clinical finding) concepts cannot have a (disorder) concept as a parent
		//Rule 2 All (disorder) concepts must be a descendant of 64572001|Disease (disorder)| 
		Set<Concept> diseases = DISEASE.getDescendants(NOT_SET);
		for (Concept c : CLINICAL_FINDING.getDescendants(NOT_SET)) {
			if (!inScope(c) || c.equals(DISEASE)) {
				continue;
			}
			String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
			if (semTag.equals("(finding)")) {
				checkForAncestorSemTag(c, issueStr);
			} else if (semTag.equals("(disorder)") && !diseases.contains(c)) {
				String legacy = getLegacyIndicator(c);
				reportAndIncrementSummary(c, !recentlyTouched.contains(c), issue2Str, legacy, isActive(c,null));
			}
		}
	}
	
	private void checkForAncestorSemTag(Concept c, String issueStr) throws TermServerScriptException {
		Set<Concept> ancestors = c.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			String semTag = SnomedUtilsBase.deconstructFSN(ancestor.getFsn())[1];
			if (semTag.equals("(disorder)")) {
				String legacy = getLegacyIndicator(c);
				reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c,null), ancestor);
				return;
			}
		}
	}

	// RP-704
	private void descriptionDialectChecks() throws TermServerScriptException {
		String issueStr = "FSN contains GB specific spelling";
		initialiseSummary(issueStr);

		DialectChecker dialectChecker = DialectChecker.create();

		for (Concept c : allActiveConceptsSorted) {
			Description fsn = c.getFSNDescription(LANG_EN);
			if (dialectChecker.containsGBSpecificTerm(fsn.getTerm())) {
				String legacy = getLegacyIndicator(c);
				reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c, null), fsn);
			}
		}
	}
	
	//RP-165
	private void textDefinitionDialectChecks() throws TermServerScriptException {
		String issueStr = "Text Definition exists in one dialect and not the other";
		initialiseSummary(issueStr);
		
		List<Description> bothDialectTextDefns = new ArrayList<>();
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely()) {
				List<Description> textDefns = c.getDescriptions(Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE);
				if (textDefns.size() > 2) {
					LOGGER.warn ("{} has {} active text definitions - check for compatibility", c, textDefns.size());
				}
				boolean hasUS = false;
				boolean hasGB = false;
				for (Description textDefn : textDefns) {
					boolean isUS = false;
					boolean isGB = false;
					if (textDefn.isPreferred(US_ENG_LANG_REFSET)) {
						isUS = true;
						hasUS = true;
					}
					if (textDefn.isPreferred(GB_ENG_LANG_REFSET)) {
						isGB = true;
						hasGB = true;
					}
					if (isUS && isGB) {
						bothDialectTextDefns.add(textDefn);
					}
					if (!isUS && !isGB) {
						LOGGER.warn("Text definition is not preferred in either dialect: {}", textDefn);
					}
				}
				if ((hasUS && !hasGB) || (hasGB && !hasUS)) {
					String legacy = getLegacyIndicator(c);
					reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c,null));
				}
			}
		}
		checkForUsGbSpecificSpelling(bothDialectTextDefns);
	}

	private void checkForUsGbSpecificSpelling(List<Description> bothDialectTextDefns) throws TermServerScriptException {
		String issueStr = "Text Definition preferred in both dialects contains US specific spelling";
		String issue2Str = "Text Definition preferred in both dialects contains GB specific spelling";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		DialectChecker dialectChecker = DialectChecker.create();
		LOGGER.debug("Checking {} both-dialect text definitions against {} dialect pairs", bothDialectTextDefns.size(), dialectChecker.size());
		
		nextDescription:
		for (Description textDefn : bothDialectTextDefns) {
			String term = " " + textDefn.getTerm().toLowerCase().replaceAll("[^A-Za-z0-9]", " ");
			Concept c = gl.getConcept(textDefn.getConceptId());
			String legacy = getLegacyIndicator(c);
			for (DialectChecker.DialectPair dialectPair : dialectChecker.getDialectPairs()) {
				if (checkDialectPair(c, dialectPair.usTermPadded, term, textDefn, issueStr, legacy) ||
					checkDialectPair(c, dialectPair.gbTermPadded, term, textDefn, issue2Str, legacy)) {
					continue nextDescription;
				}
			}
		}
	}

	private boolean checkDialectPair(Concept c, String dialectSpecificTerm, String term, Description textDefn, String issueStr, String legacy) throws TermServerScriptException {
		boolean reported = false;
		if (term.contains(dialectSpecificTerm)) {
			//If we think we've detected one, check again with URL filtered out
			String termFiltered = " " + textDefn.getTerm().toLowerCase()
					.replaceAll(URL_REGEX, "")
					.replaceAll("[^A-Za-z0-9]", " ");
			if (termFiltered.contains(dialectSpecificTerm)) {
				reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, legacy, isActive(c, null), dialectSpecificTerm, textDefn);
				reported = true;
			}
		}
		return reported;
	}

	private void nestedBracketCheck() throws TermServerScriptException {
		String issueStr = "Active description on inactive concept contains nested brackets";
		initialiseSummary(issueStr);
		Character[][] bracketPairs = new Character[][] {{'(', ')'},
			{'[',']'}};
			
		nextConcept:
		for (Concept c : allActiveConceptsSorted) {
			if (!c.isActiveSafely()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (inScope(d)) {
						for (Character[] bracketPair : bracketPairs) {
							if (containsNestedBracket(c, d, bracketPair)) {
								reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(c), isActive(c,d), d);
								continue nextConcept;
							}
						}
					}
				}
			}
		}
	}
	
	private boolean containsNestedBracket(Concept c, Description d, Character[] bracketPair) throws TermServerScriptException {
		Stack<Character> brackets = new Stack<>();
		for (Character ch: d.getTerm().toCharArray()) {
			if (ch.equals(bracketPair[0])) {  //Opening bracket
				brackets.push(ch);
				if (brackets.size() > 1) {
					return true;
				}
			} else if (ch.equals(bracketPair[1])) {  //Closing bracket
				if (brackets.isEmpty()) {
					reportAndIncrementSummary(c, !recentlyTouched.contains(c), "Closing bracket found without matching opening", getLegacyIndicator(c), isActive(c,d), d);
				} else {
					brackets.pop();
				}
			}
		}
		return false;
	}
	

	private void validateAttributeDomainModellingRules() throws TermServerScriptException {
		//RP-179 concepts using surgical approach must be surgical procedures
		String issueStr = "Concepts using |Surgical approach| must be subtypes of |surgical procedure|";
		initialiseSummary(issueStr);
		Concept type = gl.getConcept("424876005 |Surgical approach (attribute)|");
		Concept subHierarchy = gl.getConcept("387713003 |Surgical procedure (procedure)|");
		Set<Concept> subHierarchyList = cache.getDescendantsOrSelf(subHierarchy);
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely() && inScope(c)) {
				validateTypeUsedInDomain(c, type, subHierarchyList, issueStr);
			}
		}
	}

	/**
	 * Where a concept uses the specified attribute type in its modelling, 
	 * ensure that it is a descendant of the specified subhierarchy
	 * @throws TermServerScriptException 
	 */
	private void validateTypeUsedInDomain(Concept c, Concept type, Set<Concept> subHierarchyList, String issueStr) throws TermServerScriptException {
		if (SnomedUtils.hasType(CharacteristicType.INFERRED_RELATIONSHIP, c, type) && !subHierarchyList.contains(c)) {
			reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(c), isActive(c, null));
		}
	}

	private void validateAttributeTypeValueModellingRules() throws TermServerScriptException {
		String issueStr = "Finding/Procedure site cannot take a combined site value";
		initialiseSummary(issueStr);
		
		//RP-181 No finding or procedure site attribute should take a combined bodysite as the value
		List<Concept> typesOfInterest = new ArrayList<>();
		typesOfInterest.add(FINDING_SITE);
		Set<Concept> procSiteTypes = cache.getDescendantsOrSelf(gl.getConcept("363704007 |Procedure site (attribute)|"));
		typesOfInterest.addAll(procSiteTypes);
		Set<Concept> invalidValues = cache.getDescendantsOrSelf(gl.getConcept("116007004 |Combined site (body structure)|"));
		
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely() && inScope(c)) {
				for (Concept type : typesOfInterest) {
					validateTypeValueCombo(c, type, invalidValues, issueStr, false);
				}
			}
		}
	}

	private void validateInterpretsHasInterpretation() throws TermServerScriptException {
		for (Concept c : allActiveConceptsSorted) {
			if (c.isActiveSafely() && inScope(c) && (includeLegacyIssues || SnomedUtils.hasNewChanges(c))) {
				for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
					boolean conceptReported = validateRelationshipGroupForInterpretsHasInterpreation(c, g);
					if (conceptReported) {
						break;  //Move on to the next concept, only report the first infraction per concept
					}
				}
			}
		}
	}

	private boolean validateRelationshipGroupForInterpretsHasInterpreation(Concept c, RelationshipGroup g) throws TermServerScriptException {
		String issueStr = "Interprets/HasInterpretation cannot be grouped with other attributes";
		String issueStr2 = "Interprets/HasInterpretation can only exist once in a group";
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);

		//If we have an interprets or hasInterpretation, we can't have any other attributes in the group
		boolean hasInterprets = false;
		boolean hasHasInterpretation = false;
		boolean hasOtherAttribute = false;
		boolean breaksCardinalityRules = false;
		boolean reported = false;
		for (Relationship r : g.getRelationships()) {
			if (r.getType().equals(INTERPRETS)) {
				if (hasInterprets) {
					breaksCardinalityRules = true;
				}
				hasInterprets = true;
			} else if ( r.getType().equals(HAS_INTERPRETATION)) {
				if (hasHasInterpretation) {
					breaksCardinalityRules = true;
				}
				hasHasInterpretation = true;
			} else {
				hasOtherAttribute = true;
			}
		}
		if ((hasInterprets || hasHasInterpretation) && hasOtherAttribute) {
			reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(c), isActive(c, null), g);
			reported = true;
		}
		if (breaksCardinalityRules) {
			reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr2, getLegacyIndicator(c), isActive(c, null), g);
			reported = true;
		}
		return reported;
	}

	private void checkDeprecatedHierarchies() throws TermServerScriptException {
		String issueStr = "New concept created in deprecated hierarchy";
		initialiseSummary(issueStr);
		
		//RP-181 No new combined bodysite concepts should be created
		for (Concept deprecatedHierarchy : deprecatedHierarchies) {
			for (Concept c : deprecatedHierarchy.getDescendants(NOT_SET)) {
				if (!c.isReleasedSafely() && inScope(c)) {
					reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(c), isActive(c, null), deprecatedHierarchy);
				}
			}
		}
	}

	private void checkMRCMAttributeRanges() throws TermServerScriptException {
		checkMRCMTerms("MRCM Attribute Range", gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapPreCoord().values());
		checkMRCMTerms("MRCM Attribute Range", gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapPostCoord().values());
	}

	private void checkMRCMAttributeDomains() throws TermServerScriptException {
		for (Concept attribute : gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPreCoord().keySet()) {
			Map<Concept, MRCMAttributeDomain> attributeDomains = gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPreCoord().get(attribute);
			checkMRCMTerms("MRCM Attribute Domain", attributeDomains.values());
		}

		for (Concept attribute : gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPostCoord().keySet()) {
			Map<Concept, MRCMAttributeDomain> attributeDomains = gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPostCoord().get(attribute);
			checkMRCMTerms("MRCM Attribute Domain", attributeDomains.values());
		}
	}

	private void checkMRCMDomain() throws TermServerScriptException {
		checkMRCMTerms("MRCM Domain", gl.getMRCMDomainManager().getMrcmDomainMap().values());
	}

	private void checkMRCMModuleScope() throws TermServerScriptException {
		for (Concept module : gl.getMRCMModuleScopeManager().getMrcmModuleScopeMap().keySet()) {
			checkMRCMTerms("MRCM Module Scope", gl.getMRCMModuleScopeManager().getMrcmModuleScopeMap().get(module));
		}
	}
	
	private void checkMRCMTerms(String partName, Collection<? extends RefsetMember> refsetMembers) throws TermServerScriptException {
		for (RefsetMember rm : refsetMembers) {
			if (rm.isActiveSafely()) {
				Concept c = gl.getConcept(rm.getReferencedComponentId());
				for (String additionalField : rm.getAdditionalFieldNames()) {
					validateTermsInField(partName, c, rm, additionalField);
				}
			}
		}
	}

	private void validateTermsInField(String partName, Concept c, RefsetMember rm, String fieldName) throws TermServerScriptException {
		String issueStr = partName + " refset field " + fieldName + " contains inactive or unknown concept";
		String issueStr2 = partName + " refset field " + fieldName + " contains out of date FSN";
		String issueStr3 = partName + " refset field " + fieldName + " is malformed";
		String issueStr4 = partName + " refset field " + fieldName + " concept missing preferred term";
		
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		initialiseSummary(issueStr3);
		initialiseSummary(issueStr4);
		
		//Is this field all numeric?  Check concept exists if so
		String field = rm.getField(fieldName);
		if (org.ihtsdo.otf.utils.StringUtils.isNumeric(field) && field.length() > 7) {
			Concept refConcept = gl.getConcept(field, false, false);
			if (refConcept == null || !refConcept.isActiveSafely()) {
				reportAndIncrementSummary(c, isLegacySimple(rm), issueStr, getLegacyIndicator(c), isActive(c, null), field, rm.getId(), field);
			}
			return;
		}
		
		Matcher matcher = sctidFsnPattern.matcher(field);
		while (matcher.find()) {
			//Group 1 is the SCTID, group 3 is the FSN. Group 2 is optional whitespace
			if (matcher.groupCount() == 3) {
				Concept refConcept = gl.getConcept(matcher.group(1), false, false);
				if (refConcept == null || !refConcept.isActiveSafely()) {
					reportAndIncrementSummary(c, isLegacySimple(rm), issueStr, getLegacyIndicator(c), isActive(c, null), refConcept == null ? matcher.group(1) : refConcept, rm.getId(), field);
				} else {
					if (refConcept.getPreferredSynonym(US_ENG_LANG_REFSET) == null) {
						reportAndIncrementSummary(c, isLegacySimple(rm), issueStr4, getLegacyIndicator(c), isActive(c, null), refConcept, rm.getId(), field);
					} else {
						String fsn = matcher.group(3);
						if (fsn == null) {
							reportAndIncrementSummary(c, isLegacySimple(rm), issueStr3, getLegacyIndicator(c), isActive(c, null), refConcept, rm.getId(), field);
						} else if (!refConcept.getFsn().equals(fsn)) {
							//Sometimes we use the PT.  Check on the rules for when we use each one.
							if (!fsn.equals(refConcept.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm())) {
								reportAndIncrementSummary(c, isLegacySimple(rm), issueStr2, getLegacyIndicator(c), isActive(c, null), refConcept, "Text in refset field: " + fsn, rm.getId(), field);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * If the given concept uses the particular type, checks if that type is in (or must not be in)
	 * the list of specified values
	 * @throws TermServerScriptException 
	 */
	private void validateTypeValueCombo(Concept c, Concept type, Set<Concept> values, String issueStr,
			boolean mustBeIn) throws TermServerScriptException {
		Set<Relationship> relsWithType = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, type, ActiveState.ACTIVE);
		for (Relationship relWithType : relsWithType) {
			//Must the value be in, or must the value be NOT in our list of values?
			boolean isIn = values.contains(relWithType.getTarget());
			if (!isIn == mustBeIn) {
				reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(relWithType), isActive(c, relWithType), relWithType);
			}
		}
	}
	
	
	//RP-180
	private void neverGroupTogether() throws TermServerScriptException {
		Concept[][] neverTogetherList = new Concept[][] 
				{
					{ gl.getConcept("363589002 |Associated procedure|"), gl.getConcept("408729009 |Finding context|")},
					{ gl.getConcept("408730004 |Procedure context|"), gl.getConcept("246090004 |Associated finding|")}
				};
			
		for (Concept[] neverTogether : neverTogetherList) {
			String issueStr = "Attributes " + neverTogether[0].toStringPref() + " and " + neverTogether[1].toStringPref() + " must not appear in same group";
			initialiseSummary(issueStr);
			for (Concept c : allActiveConceptsSorted) {
				if (c.isActiveSafely() && inScope(c)) {
					if (appearInSameGroup(c, neverTogether[0], neverTogether[1])) {
						reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(c), isActive(c, null));
					}
				}
			}
		}
	}

	//RP-180
	private void domainMustNotUseType() throws TermServerScriptException {
		//FORMAT 0 - Domain 1 - Disallowed Attribute 2 - Unless also a member of domain
		Concept[][] domainTypeIncompatibilities = new Concept[][] 
				{
					{ gl.getConcept("413350009 |Finding with explicit context|"), gl.getConcept("363589002 |Associated procedure|"), gl.getConcept("129125009 |Procedure with explicit context|")},
					{ gl.getConcept("129125009 |Procedure with explicit context|"), gl.getConcept("408729009 |Finding context|"),  gl.getConcept("413350009 |Finding with explicit context|")}
				};
		for (Concept[] domainType : domainTypeIncompatibilities) {
			String issueStr = "Domain " + domainType[0] + " should not use attribute type: " + domainType[1];
			initialiseSummary(issueStr);
			for (Concept c : domainType[0].getDescendants(NOT_SET)) {
				if (c.isActiveSafely() && inScope(c)) {
					if (SnomedUtils.hasType(CharacteristicType.INFERRED_RELATIONSHIP, c, domainType[1])) {
						//RP-574 But is this concept also a type of a domain that would allow this attribute?
						Set<Concept> ancestors = c.getAncestors(NOT_SET);
						if (!ancestors.contains(domainType[2])) {
							reportAndIncrementSummary(c, !recentlyTouched.contains(c), issueStr, getLegacyIndicator(c), isActive(c, null));
						}
					}
				}
			}
		}
		
	}

	private boolean appearInSameGroup(Concept c, Concept c1, Concept c2) {
		//Work through all inferred groups.  Are c1 and c2 types both present?
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
			boolean c1Present = false;
			boolean c2Present = false;
			for (Relationship r : g.getRelationships()) {
				if (r.getType().equals(c1)) {
					c1Present = true;
				} else if (r.getType().equals(c2)) {
					c2Present = true;
				}
			}
			if (c1Present && c2Present) {
				return true;
			}
		}
		return false;
	}



	class ConcernLevel {
		//Need an object to wrap an integer so we can pass it into a function multiple times
		private int concern = 0;

		ConcernLevel() {
		}


		public void increment() {
			concern++;
		}

		public void decrement() {
			concern--;
		}

		public void increaseConcern(int increment) {
			concern += increment;
		}

		public void decrease(int decrement) {
			concern -= decrement;
		}

		public boolean isConcerning(int threshold) {
			return concern >= threshold;
		}
	}

}
