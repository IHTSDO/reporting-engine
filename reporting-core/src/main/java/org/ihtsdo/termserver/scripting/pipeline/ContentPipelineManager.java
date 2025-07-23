package org.ihtsdo.termserver.scripting.pipeline;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConceptNull;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConceptNull;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConceptWithDefaultMap;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ContentPipelineManager extends TermServerScript implements ContentPipeLineConstants {

	public static final String CHANGES_SINCE_LAST_ITERATION = "Changes since last iteration";
	public static final String HIGHEST_USAGE_COUNTS = "Highest usage counts";
	public static final String CONTENT_COUNT = "Content counts";
	public static final String INTERNAL_MAP_COUNT = "Internal map counts";
	public static final String REFSET_COUNT = "Refset counts";
	public static final String FAILED_TO_LOAD = "Failed to load ";
	public static final String LANG_REFSET_REMOVAL = "Lang Refset Removal";

	public static final String DUMMY_EXTERNAL_IDENTIFIER = "DUMMY_EXTERNAL_IDENTIFIER";
	
	public static final String FSN_FAILURE = "FSN indicates failure";

	// Regular expression to find tokens within square brackets
	private static final String ALL_CAPS_SLOT_REGEX = "\\[([A-Z]+)\\]";
	private static final Pattern allCapsSlotPattern = Pattern.compile(ALL_CAPS_SLOT_REGEX);

	public void recordSuccessfulModelling(TemplatedConcept tc) {
		successfullyModelled.add(tc);
	}

	public boolean shouldIncludeShortNameDescription() {
		return includeShortNameDescription;
	}

	private enum RunMode { NEW, INCREMENTAL_DELTA, INCREMENTAL_API}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentPipelineManager.class);
	
	protected static final RunMode runMode = RunMode.INCREMENTAL_DELTA;
	
	protected static final int FILE_IDX_CONCEPT_IDS = 7;
	protected static final int FILE_IDX_DESC_IDS = 8;
	protected static final int FILE_IDX_REL_IDS = 9;
	
	protected Map<String, ExternalConcept> externalConceptMap = new HashMap<>();
	protected AttributePartMapManager attributePartMapManager;
	protected Map<String, Part> partMap = new HashMap<>();
	protected Map<String, String> partMapNotes = new HashMap<>();
	protected Map<Part, Set<ExternalConcept>> missingPartMappings = new HashMap<>();
	
	protected Concept scheme;
	protected String namespace;
	protected String externalContentModuleId;
	protected Rf2ConceptCreator conceptCreator;
	protected int additionalThreadCount = 0;
	protected Set<TemplatedConcept> successfullyModelled = new HashSet<>();
	protected Set<TemplatedConcept> inactivatedConcepts = new HashSet<>();
	protected boolean includeShortNameDescription = true;

	private  Map<String, Map<String, Integer>> summaryCountsByCategory = new HashMap<>();

	protected Set<ComponentType> skipForComparison = Set.of(
			ComponentType.INFERRED_RELATIONSHIP,
			ComponentType.SIMPLE_REFSET_MEMBER,
			ComponentType.LANGREFSET);

	protected List<TemplatedConcept.IterationIndicator> activeIndicators = List.of(
			TemplatedConcept.IterationIndicator.NEW,
			TemplatedConcept.IterationIndicator.UNCHANGED,
			TemplatedConcept.IterationIndicator.MODIFIED,
			TemplatedConcept.IterationIndicator.RESURRECTED);

	protected void ingestExternalContent(String[] args) throws TermServerScriptException {
		try {
			runStandAlone = false;
			getGraphLoader().setExcludedModules(new HashSet<>());
			getArchiveManager().setLoadOtherReferenceSets(true);
			getArchiveManager().setRunIntegrityChecks(false);
			getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);  //Needed for working out if we're deleteing or inactivating
			init(args);
			loadProjectSnapshot(false);
			postInit();
			conceptCreator = Rf2ConceptCreator.build(this, getInputFile(FILE_IDX_CONCEPT_IDS), getInputFile(FILE_IDX_DESC_IDS), getInputFile(FILE_IDX_REL_IDS), this.getNamespace());
			conceptCreator.initialiseGenerators(new String[]{"-nS",this.getNamespace(), "-m", getExternalContentModuleId()});
			loadSupportingInformation();
			importPartMap();
			preModelling();
			doModeling();
			checkSpecificConcepts();
			TemplatedConcept.reportStats(getTab(TAB_SUMMARY));
			if (tabExists(TAB_MAP_ME)) {
				reportMissingMappings(getTab(TAB_MAP_ME));
			}
			reportIncludedExcludedConcepts(getTab(TAB_STATS));
			flushFiles(false);
			switch (runMode) {
				case NEW: outputAllConceptsToDelta();
					break;
				case INCREMENTAL_API, INCREMENTAL_DELTA:
					determineChangeSet();
					break;
				default:
					throw new TermServerScriptException("Unrecognised Run Mode :" + runMode);
			}
			postModelling();
			reportSummaryCounts();
			conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
		} finally {
			while (additionalThreadCount > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			finish();
			if (conceptCreator != null) {
				conceptCreator.finish();
			}
		}
	}

	public String getExternalContentModuleId() {
		return externalContentModuleId;
	}

	protected void preModelling() throws TermServerScriptException {
		//Override this method in base class to do some setup prior to modeling
	}

	protected void postModelling() throws TermServerScriptException {
		//Override this method in base class to do some final work with the sucessfully modelled concepts
		//and also those being inactivated eg sorting out the ORD/OBS Refset Members in LOINC
	}

	private boolean tabExists(String tabName) {
		try {
			getTab(tabName);
			return true;
		} catch (TermServerScriptException e) {
			return false;
		}
	}

	protected abstract String getContentType();

	protected abstract void loadSupportingInformation() throws TermServerScriptException;

	protected abstract void importPartMap() throws TermServerScriptException;

	protected List<String> getExternalConceptsToModel() throws TermServerScriptException {
		return new ArrayList<>(externalConceptMap.keySet());
	}

	protected void doModeling() throws TermServerScriptException {
		for (String externalIdentifier : getExternalConceptsToModel()) {
			TemplatedConcept templatedConcept = modelExternalConcept(externalIdentifier);
			validateTemplatedConcept(externalIdentifier, templatedConcept);
			templatedConcept.populateAlternateIdentifier();
			if (conceptSufficientlyModeled(getContentType(), externalIdentifier, templatedConcept)) {
				recordSuccessfulModelling(templatedConcept);
			}
		}
	}

	protected TemplatedConcept modelExternalConcept(String externalIdentifier) throws TermServerScriptException {
		if (externalIdentifier.equals("108027-4")) {
			LOGGER.debug("Check grouper behaviour");
		}

		if (externalIdentifier.equals("20695-3")) {
			LOGGER.debug("Check term capitalization");
		}

		ExternalConcept externalConcept = externalConceptMap.get(externalIdentifier);
		if (!confirmExternalIdentifierExists(externalIdentifier) ||
				containsObjectionableWord(externalConcept)) {
			return null;
		}

		//Is this a transformed concept that's being maintained manually?  Return what is already there if so.
		if (MANUALLY_MAINTAINED_ITEMS.containsKey(externalIdentifier)) {
			TemplatedConcept tc = TemplatedConceptWithDefaultMap.create(externalConcept, scheme.getId(), "(observable entity)");
			tc.setConcept(gl.getConcept(MANUALLY_MAINTAINED_ITEMS.get(externalIdentifier)));
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.MANUAL);
			tc.populateAlternateIdentifier();
			//If we don't already have this alt identifier, we'll output it now, as we don't output changes for manually maintained items
			if (!gl.getSchemaMap(scheme).containsKey(externalIdentifier)) {
				conceptCreator.outputAltId(tc.getConcept(), scheme.getId());
			}
			return tc;
		}

		TemplatedConcept tc = getAppropriateTemplate(externalConcept);

		if (!(tc instanceof TemplatedConceptNull)) {
			tc.populateTemplate();
		} else if (externalConcept.isHighestUsage()) {
			//This is a 'highest usage' term, which is out of scope
			incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS, "Highest Usage Out of Scope");
		}
		return tc;
	}

	protected abstract String[] getTabNames();

	protected abstract Set<String> getObjectionableWords();

	private void reportSummaryCounts() throws TermServerScriptException {
		int summaryTabIdx = getTab(TAB_SUMMARY);
		report(summaryTabIdx, "");
		//Work through each category (sorted) and then output each summary Count for that category
		summaryCountsByCategory.keySet().stream()
				.sorted()
				.forEach(cat -> {
					reportSafely(summaryTabIdx, cat);
					Map<String, Integer> summaryCounts = summaryCountsByCategory.get(cat);
					summaryCounts.keySet().stream()
							.sorted()
							.forEach(summaryItem -> reportSafely(summaryTabIdx, "", summaryItem, summaryCounts.get(summaryItem)));
				});
	}

	private void outputAllConceptsToDelta() throws TermServerScriptException {
		for (TemplatedConcept tc : successfullyModelled) {
			Concept concept = tc.getConcept();
			try {
				conceptCreator.writeConceptToRF2(getTab(TAB_IMPORT_STATUS), concept, tc.getExternalIdentifier());
			} catch (Exception e) {
				report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getExternalIdentifier(), e);
			}
		}
		conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
	}

	protected void determineChangeSet() throws TermServerScriptException {
		LOGGER.info("Determining change set for {} successfully modelled concepts", successfullyModelled.size());

		Set<String> externalIdentifiersProcessed = new HashSet<>();

		//Sort so that subsequent spreadsheets are somewhat comparable
		List<TemplatedConcept> sortedModelled = successfullyModelled.stream()
				.sorted(Comparator.comparing(TemplatedConcept::getExternalIdentifier))
				.toList();
		
		for (TemplatedConcept tc : sortedModelled) {
			incrementSummaryCount("Counts per template", tc.getClass().getSimpleName());
			//Skip any concepts that are externally maintained
			if (!MANUALLY_MAINTAINED_ITEMS.containsKey(tc.getExternalIdentifier())){
				determineChanges(tc, externalIdentifiersProcessed);
			} else {
				//We'll minimally report the manually maintained concepts
				Concept mmc = gl.getConcept(MANUALLY_MAINTAINED_ITEMS.get(tc.getExternalIdentifier()));
				String scg = mmc.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				String descStr = SnomedUtils.getDescriptionsToString(mmc);

				report(getTab(TAB_PROPOSED_MODEL_COMPARISON),
						tc.getExternalIdentifier(),
						tc.getConcept().getId(),
						tc.getIterationIndicator(),
						tc.getClass().getSimpleName(),
						"N/A",
						"N/A",
						descStr,
						"N/A",
						scg);
			}
		}

		determineInactivations(sortedModelled);
	}

	private void determineInactivations(List<TemplatedConcept> sortedModelled) throws TermServerScriptException {
		//What external codes do we currently have that we _aren't_ going forward with.
		//Those need to be inactivated
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		Set<String> inactivatingCodes =  new HashSet<>(altIdentifierMap.keySet());
		inactivatingCodes.removeAll(sortedModelled.stream().map(m -> m.getExternalIdentifier()).collect(Collectors.toSet()));
		for (String inactivatingCode : inactivatingCodes) {
			processInactivation(inactivatingCode, altIdentifierMap);
		}
	}

	private void processInactivation(String inactivatingCode, Map<String, String> altIdentifierMap) throws TermServerScriptException {
		String existingConceptSCTID = altIdentifierMap.get(inactivatingCode);
		Concept existingConcept = gl.getConcept(existingConceptSCTID, false, false);
		if (existingConcept != null) {
			inactivateConcept(existingConcept);
			conceptCreator.outputRF2Inactivation(existingConcept);
		}

		//Create a Templated Concept to record the inactivation
		//But does this external code even still existing in the external code system?
		TemplatedConcept inactivation;
		ExternalConcept ec = externalConceptMap.get(inactivatingCode);
		if (ec == null) {
			LOGGER.warn("Did not find an external concept for inactivating code: {}", inactivatingCode);
			inactivation = TemplatedConceptNull.createNull(inactivatingCode, null);
		} else {
			inactivation = TemplatedConceptNull.create(ec);
		}
		inactivation.setConcept(existingConcept);
		inactivatedConcepts.add(inactivation);

		doProposedModelComparison(inactivation);
		incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, TemplatedConcept.IterationIndicator.REMOVED.toString());

		//Might not be obvious: the alternate identifier continues to exist even when the concept becomes inactive
		//So - temporarily again - we'll normalize the scheme id
		//Temporarily correct all Alternate Identifiers
		if (existingConcept != null) {
			existingConcept.setAlternateIdentifiers(new HashSet<>());
			existingConcept.addAlternateIdentifier(inactivatingCode, scheme.getId());
		}
	}

	private void determineChanges(TemplatedConcept tc, Set<String> externalIdentifiersProcessed) throws TermServerScriptException {
		Concept concept = tc.getConcept();
		externalIdentifiersProcessed.add(tc.getExternalIdentifier());

		//Do we already have this concept?  Also, it might use freshly modelled concepts internally which need to have IDs assigned
		//before we can compare their axioms
		Concept existingConcept = getExistingConceptAndPopulateReferencedConcepts(tc);

		//We need to make any adjustments to inferred relationships before we lose the stated ones in the transformation to axioms
		adjustInferredRelationships(concept, existingConcept);

		if (existingConcept == null) {
			//This concept is entirely new, prepare to output all
			if (runMode.equals(RunMode.INCREMENTAL_DELTA)) {
				conceptCreator.populateIds(concept);
			}

			if (tc.getIterationIndicator() == null) {
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.NEW);
			}
			convertStatedRelationshipsToAxioms(concept, true, true);
			concept.setAxiomEntries(AxiomUtils.convertClassAxiomsToAxiomEntries(concept));
		} else {
			determineChangesWithExistingConcept(tc);
		}

		//Update the summary count based on the comparison to the previous iteration
		incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, tc.getIterationIndicator().toString());

		//Is this a high usage concept?
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighUsage()) {
			incrementSummaryCount(HIGHEST_USAGE_COUNTS, "Active with high usage");
		}
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighestUsage()) {
			incrementSummaryCount(HIGHEST_USAGE_COUNTS,"Active with highest usage");
		}
		doProposedModelComparison(tc);

		if (!tc.getIterationIndicator().equals(TemplatedConcept.IterationIndicator.UNCHANGED)) {
			conceptCreator.outputRF2(getTab(TAB_IMPORT_STATUS), tc.getConcept(), "");
		}

		if (tc.existingConceptHasInactivations()) {
			conceptCreator.outputRF2Inactivation(tc.getExistingConcept());
		}
	}

	private Concept getExistingConceptAndPopulateReferencedConcepts(TemplatedConcept tc) throws TermServerScriptException {
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		String existingConceptSCTID = altIdentifierMap.get(tc.getExternalIdentifier());

		Concept existingConcept = getExistingConceptIfExists(existingConceptSCTID, tc);
		tc.setExistingConcept(existingConcept);

		for (Relationship r : tc.getConcept().getRelationships()) {
			Concept targetValue = r.getTarget();
			//Do we have a null id, or a temporary UUID?
			if (targetValue.getConceptId() == null || targetValue.getConceptId().length() > SCTID_MAX_LENGTH) {
				//Can we find that concept via what it might have been created for?
				String targetExternalId = findExternalIdentifierForModelledConcept(targetValue);
				if (targetExternalId == null) {
					throw new TermServerScriptException("Unable to find external identifier for modelling in concept " + tc.getConcept().toExpression(CharacteristicType.STATED_RELATIONSHIP));
				} else {
					String existingTargetSCTID = altIdentifierMap.get(targetExternalId);
					Concept existingTarget = getExistingConceptIfExists(existingTargetSCTID, tc);
					if (existingTarget == null) {
						//We need to give this concept an ID before we can form an axiom
						conceptCreator.populateComponentId(targetValue,targetValue, externalContentModuleId);
					} else {
						r.setTarget(existingTarget);
					}
				}
			}
		}
		return existingConcept;
	}

	private String findExternalIdentifierForModelledConcept(Concept c) {
		for (TemplatedConcept tc : successfullyModelled) {
			if (tc.getConcept().equals(c)) {
				return tc.getExternalIdentifier();
			}
		}
		return null;
	}

	/**
	 * @return true if changes are detected
	 */
	private void determineChangesWithExistingConcept(TemplatedConcept tc) throws TermServerScriptException {
		SnomedUtils.getAllComponents(tc.getConcept()).forEach(c -> {
			c.setClean();
			//Normalise module
			c.setModuleId(conceptCreator.getTargetModuleId());
		});

		//We need to populate the concept SCTID before we can create axiom entries
		tc.getConcept().setId(tc.getExistingConcept().getId());
		//And we can apply that to the alternate identifiers early on so they don't show up as a change
		tc.getConcept().getAlternateIdentifiers()
				.forEach(a -> a.setReferencedComponentId(tc.getExistingConcept().getId()));
		//Copy the axiom entry from the existing concept so relationship changes can be applied there
		tc.getConcept().setAxiomEntries(tc.getExistingConcept().getAxiomEntries(ActiveState.ACTIVE, false));
		convertStatedRelationshipsToAxioms(tc.getConcept(), true, true);
		tc.getConcept().setAxiomEntries(AxiomUtils.convertClassAxiomsToAxiomEntries(tc.getConcept()));

		List<ComponentComparisonResult> componentComparisonResults = SnomedUtils.compareComponents(tc.getExistingConcept(), tc.getConcept(), skipForComparison);
		if (ComponentComparisonResult.hasChanges(componentComparisonResults)) {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.MODIFIED);
		} else {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.UNCHANGED);
			tc.addDifferenceFromExistingConcept("All Unchanged");
		}

		for (ComponentComparisonResult componentComparisonResult : componentComparisonResults) {
			processComponentComparison(tc, componentComparisonResult);
		}
	}

	private void processComponentComparison(TemplatedConcept tc, ComponentComparisonResult componentComparisonResult) throws TermServerScriptException {
		Component existingComponent = componentComparisonResult.getLeft();
		Component newlyModelledComponent = componentComparisonResult.getRight();

		if (!componentComparisonResult.isMatch()) {
			tc.addDifferenceFromExistingConcept(componentComparisonResult.getComponentTypeStr());
		}

		//If we have both, then just output the change
		if (existingComponent != null && newlyModelledComponent != null) {
			newlyModelledComponent.setId(existingComponent.getId());
			if (componentComparisonResult.isMatch()) {
				newlyModelledComponent.setClean();
			} else {
				newlyModelledComponent.setDirty();
			}

			//Any component specific actions?
			switch (existingComponent.getComponentType()) {
				case CONCEPT:
					alignAlternateIdentifier(tc.getConcept(), tc.getExistingConcept());
					//Copy any simple refset members from the existing concept to the new one eg  ORD/OBS refset
					tc.getConcept().setOtherRefsetMembers(tc.getExistingConcept().getOtherRefsetMembers());
					break;
				case DESCRIPTION:
					Description newDesc = (Description)newlyModelledComponent;
					newDesc.setConceptId(tc.getExistingConcept().getId());
					alignLangRefsetEntries(newDesc, (Description)existingComponent);
					break;
				default:
					break;
			}
		} else if (existingComponent != null && newlyModelledComponent == null) {
			//If we have an existing component, and it has no newly Modelled counterpart, then inactivate it
			existingComponent.setActive(false);
			existingComponent.setDirty();
			tc.setExistingConceptHasInactivations(true);
		} else {
			//If we only have a newly modelled component, give it an id
			//and prepare to output
			conceptCreator.populateComponentId(tc.getExistingConcept(), newlyModelledComponent, externalContentModuleId);
			newlyModelledComponent.setDirty();
		}
	}

	private void alignAlternateIdentifier(Concept cNew, Concept cExisting) {
		//If we have the same scheme and altId, then we just need to copy over the previous memberId
		//and then we can mark the altId as clean and no need to output it again
		for (AlternateIdentifier altId : cNew.getAlternateIdentifiers()) {
			AlternateIdentifier existingAltId = cExisting.getAlternateIdentifierForScheme(altId.getIdentifierSchemeId());
			if (existingAltId != null && existingAltId.getId().equals(altId.getId())) {
				altId.setId(existingAltId.getId());
				altId.setClean();
			}
		}
	}

	private void alignLangRefsetEntries(Description newDesc, Description oldDesc) {
		//For each refsetId, pinch the ID from the existing description and apply it to the new one
		for (LangRefsetEntry lre : oldDesc.getLangRefsetEntries(ActiveState.ACTIVE)) {
			List<LangRefsetEntry> newLres = newDesc.getLangRefsetEntries(ActiveState.ACTIVE, lre.getRefsetId());
			//The new description might not have an entry for this refsetId, eg if we've removed en-gb
			if (newLres.isEmpty()) {
				//If we've removed the en-gb lang refset or similar, then we need to inactivate the existing one
				lre.setActive(false);
				lre.setDirty();
			} else {
				LangRefsetEntry newLre = newLres.get(0);
				newLre.setId(lre.getId());
				newLre.setReferencedComponentId(oldDesc.getId());
				//But, has the acceptability changed?  If so, we need to output this as a change
				if (!newLre.getAcceptabilityId().equals(lre.getAcceptabilityId())) {
					newLre.setDirty();
				}
			}
		}
	}

	private Concept getExistingConceptIfExists(String existingConceptSCTID, TemplatedConcept tc) throws TermServerScriptException {
		Concept existingConcept = null;
		if (existingConceptSCTID != null) {
			existingConcept = gl.getConcept(existingConceptSCTID, false, false);
			if (existingConcept == null) {
				String msg = "Alternate identifier " + tc.getExternalIdentifier() + " --> " + existingConceptSCTID + " but existing concept not found.  Did it get deleted?  Reusing ID.";
				addFinalWords(msg);
				tc.getConcept().setId(existingConceptSCTID);
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.RESURRECTED);
			} else {
				//Temporarily correct all Alternate Identifiers
				existingConcept.setAlternateIdentifiers(new HashSet<>());
				existingConcept.addAlternateIdentifier(tc.getExternalIdentifier(), scheme.getId());
			}
		}
		return existingConcept;
	}

	private boolean adjustInferredRelationships(Concept concept, Concept existingConcept) {
		boolean changesMade = false;
		if (existingConcept == null) {
			conceptCreator.copyStatedRelsToInferred(concept);
			changesMade = true;
		} else {
			//TEMPORARY CODE.   We can remove this after our first publication
			//For existing concepts we're going to group inferred relationships if required
			for (Relationship r : existingConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getGroupId() > 1) {
					Relationship inferredRelOnNewConcept = r.cloneWithIds();
					inferredRelOnNewConcept.setGroupId(1);
					concept.addRelationship(inferredRelOnNewConcept);
					changesMade = true;
				}
			}
		}
		return changesMade;
	}

	private List<String> inactivateConcept(Concept c) {
		List<String> differencesList = new ArrayList<>();
		//To inactivate a concept we need to inactivate the concept itself and the OWL axiom.
		//The descriptions remain active, and we'll let classification sort out the inferred relationships
		if (c.isActiveSafely()) {
			c.setActive(false);  //This will inactivate the concept and all relationships
			InactivationIndicatorEntry ii = InactivationIndicatorEntry.withDefaults(c, SCTID_INACT_OUTDATED);
			ii.setModuleId(externalContentModuleId);
			c.addInactivationIndicator(ii);
			differencesList.add("CONCEPT");
		}
		
		for (AxiomEntry a : c.getAxiomEntries(ActiveState.ACTIVE, true)) {
			a.setActive(false);
			differencesList.add("AXIOM");
		}
		return differencesList;
	}
	
	protected void reportIncludedExcludedConcepts(int tabIdx) throws TermServerScriptException {
		Set<String> successfullyModelledExternalIds = successfullyModelled.stream()
				.map(tc -> tc.getExternalIdentifier())
				.collect(Collectors.toSet());

		//Collect both included and excluded terms by property
		Map<String, List<ExternalConcept>> included = externalConceptMap.values().stream()
				.filter(lt -> successfullyModelledExternalIds.contains(lt.getExternalIdentifier()))
				.collect(Collectors.groupingBy(this::decorateProperty));

		Map<String, List<ExternalConcept>> excluded = externalConceptMap.values().stream()
				.filter(lt -> !successfullyModelledExternalIds.contains(lt.getExternalIdentifier()))
				.collect(Collectors.groupingBy(this::decorateProperty));

		Set<String> properties = new LinkedHashSet<>(included.keySet());
		properties.addAll(excluded.keySet());

		for (String property : properties) {
			int includedCount = included.getOrDefault(property, new ArrayList<>()).size();
			int includedInTop2KCount = included.getOrDefault(property, new ArrayList<>()).stream()
					.filter(ExternalConcept::isHighestUsage)
					.toList().size();
			int excludedCount = excluded.getOrDefault(property, new ArrayList<>()).size();
			int excludedInTop2KCount = excluded.getOrDefault(property, new ArrayList<>()).stream()
					.filter(ExternalConcept::isHighestUsage)
					.toList().size();
			report(tabIdx, property, inScope(undecorate(property)), includedCount, includedInTop2KCount, excludedCount, excludedInTop2KCount);
		}
	}

	private String undecorate(String property) {
		return property.split(" ")[0];
	}

	private String decorateProperty(ExternalConcept ec) {
		String decoratedProperty = ec.getProperty();
		if (ec instanceof LoincTerm lt) {
			decoratedProperty += " " + lt.getClassType();
		}
		return decoratedProperty;
	}

	public int getTab(String tabName) throws TermServerScriptException {
		String[] tabNames = getTabNames();
		for (int i = 0; i < tabNames.length; i++) {
			if (tabNames[i].equals(tabName)) {
				return i;
			}
		}
		throw new TermServerScriptException("Tab '" + tabName + "' not recognised");
	}
	
	protected String getNamespace() {
		return namespace;
	}

	public void incrementSummaryCount(String category, String summaryItem) {
		incrementSummaryCount(category, summaryItem, 1);
	}

	public void incrementSummaryCount(String category, String summaryItem, int increment) {
		//Increment the count for this summary item, in the appropriate category
		Map<String, Integer> summaryCounts = summaryCountsByCategory.computeIfAbsent(category, k -> new HashMap<>());
		summaryCounts.merge(summaryItem, increment, Integer::sum);
	}

	public static final List<String> ITEMS_OF_INTEREST =
			List.of("882-1","881-3","61151-7","1751-7","9318-7","1759-0","33037-3","41276-7","10466-1",
					"5767-9","33511-7","5769-5","11555-0","24321-2","1968-7","925-8","933-2","936-5",
					"62292-8","14155-6","9830-1","9322-9","2106-3","14979-9","5902-2","6301-6","38875-1",
					"50553-7","24323-8","35591-7","49024-3","39004-7","5787-7","11277-1","33219-7","12258-0",
					"788-0","30384-2","30385-9","21000-5","785-6","28539-5","786-4","28540-3","787-2",
					"30428-7","6742-1","4537-7","30341-2","58413-6","19048-8","27353-2","53553-4","49541-6",
					"48058-2","33914-3","77147-7","62238-1","48643-1","48642-3","51584-1","53115-2","34165-1",
					"38518-7","71695-1","4544-3","31100-1","56888-1","2500-7","2502-3","2532-0","24318-8",
					"26485-3","53797-7","664-3","2695-5","2708-6","32623-1","28542-9","2890-2","8251-1",
					"2965-2","5811-5","50562-8","53326-5","66746-9","3097-3","44734-2");

	public static final Map<String, String> MANUALLY_MAINTAINED_ITEMS = Map.of(
			"8251-1", "580221010000109 |Service comment (observable entity)|",
			"49024-3", "580231010000107 |Differential cell count method - Blood (observable entity)|",
			"49541-6", "580241010000104 |Fasting status - Reported (observable entity)|",
			"14155-6", "580261010000100 |Cholesterol in LDL [Percentile] (observable entity)|",
			"9322-9", "580251010000102 |Cholesterol.total/Cholesterol in HDL [Percentile] (observable entity)| ",
			"56888-1", "570211010000106 |Presence of Human immunodeficiency virus 1 antibody and/or Human immunodeficiency virus 2 antibody and/or Human immunodeficiency virus 1 protein 24 antigen in serum or plasma at point in time by immunoassay (observable entity)| "
	);

	public static String getSpecialInterestIndicator(String externameIdentifer) {
		return ITEMS_OF_INTEREST.contains(externameIdentifer) ? "Y" : "";
	}

	private static final String TOP_88 = "Top 88";
	private static final String TOP_2K = "Top 2000";
	private void checkSpecificConcepts() throws TermServerScriptException {
		reportStatusOfSpecificItemsOfInterest(TOP_88, ITEMS_OF_INTEREST);
		//Generate set of external concepts which are of highest interest
		List<String> top20KExternalConcepts = externalConceptMap.values().stream()
				.filter(ExternalConcept::isHighestUsage)
				.map(ExternalConcept::getExternalIdentifier)
				.toList();
		reportStatusOfSpecificItemsOfInterest(TOP_2K, top20KExternalConcepts);
	}

	private void reportStatusOfSpecificItemsOfInterest(String reportKey, List<String> itemsOfInterest) throws TermServerScriptException {
		for (String loincNum : itemsOfInterest) {
			boolean found = false;
			if (MANUALLY_MAINTAINED_ITEMS.containsKey(loincNum)) {
				report(getTab(ContentPipeLineConstants.TAB_ITEMS_OF_INTEREST),reportKey, loincNum, "Modelled manually", MANUALLY_MAINTAINED_ITEMS.get(loincNum));
				continue;
			}

			for (TemplatedConcept tc : successfullyModelled) {
				if (tc.getExternalIdentifier().equals(loincNum)) {
					report(getTab(ContentPipeLineConstants.TAB_ITEMS_OF_INTEREST), reportKey, tc.getExternalIdentifier(), "Modelled",tc.getConcept());
					found = true;
					break;
				}
			}

			if (!found) {
				report(getTab(ContentPipeLineConstants.TAB_ITEMS_OF_INTEREST),reportKey, loincNum, "Not Modelled");
			}
		}
	}



	public abstract List<String> getMappingsAllowedAbsent();
	
	protected void reportMissingMappings(int tabIdx) throws TermServerScriptException {
		for (Map.Entry<Part, Set<ExternalConcept>> entry : missingPartMappings.entrySet()) {
			Part part = entry.getKey();
			Set<ExternalConcept> externalConcepts = entry.getValue();
			String[] highUsageIndicators = getHighUsageIndicators(externalConcepts);
			report(tabIdx, 
					part.getPartNumber(),
					part.getPartName(),
					part.getPartTypeName(),
					highUsageIndicators[0],
					highUsageIndicators[1],
					"N/A",
					externalConcepts.size(),
					highUsageIndicators[2],
					highUsageIndicators[3],
					highUsageIndicators[4]);
		}
	}

	protected abstract String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts);

	public ExternalConcept getExternalConcept(String externalIdentifier) {
		return externalConceptMap.get(externalIdentifier);
	}
	
	public void addMissingMapping(String partNum, String externalIdentifier) {
		Part part = partMap.get(partNum);
		if (part == null) {
			part = new Part(partNum, "Unknown Type", "Unknown to part input file.");
		}
		missingPartMappings.computeIfAbsent(part, key -> new HashSet<>())
							.add(getExternalConcept(externalIdentifier));
	}

	public Map<String, ExternalConcept> getExternalConceptMap() {
		return externalConceptMap;
	}
	
	public AttributePartMapManager getAttributePartManager() {
		return attributePartMapManager;
	}
	
	protected void doProposedModelComparison(TemplatedConcept tc) throws TermServerScriptException {
		Concept proposedConcept = tc.getConcept();
		Concept existingConcept = tc.getExistingConcept();
		ExternalConcept externalConcept = tc.getExternalConcept();

		String previousSCG = existingConcept == null ? "N/A" : existingConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedSCG = proposedConcept == null ? "N/A" : proposedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedDescriptionsStr = proposedConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(proposedConcept);
		
		//We might have inactivated descriptions in the existing concept if they've been changed, so
		String previousDescriptionsStr = existingConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(existingConcept, true);
		String existingConceptId = existingConcept == null ? "N/A" : existingConcept.getId();
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON),
				tc.getExternalIdentifier(),
				proposedConcept != null ? proposedConcept.getId() : existingConceptId,
				tc.getIterationIndicator(),
				tc.getClass().getSimpleName(),
				tc.getDifferencesFromExistingConceptWithMultiples(),
				proposedDescriptionsStr,
				previousDescriptionsStr,
				proposedSCG, 
				previousSCG,
				externalConcept.getCommonColumns());
	}

	public abstract TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept)
			throws TermServerScriptException ;
	
	protected boolean conceptSufficientlyModeled(String contentType, String externalIdentifier, TemplatedConcept templatedConcept) throws TermServerScriptException {
		if (templatedConcept != null
				&& !(templatedConcept instanceof TemplatedConceptNull)
				&& !templatedConcept.getConcept().hasIssue(FSN_FAILURE)
				&& !templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
			if (templatedConcept.getConcept().getRelationships().isEmpty()) {
				throw new TermServerScriptException("Missing relationships for concept " + contentType + " " + externalIdentifier);
			}

			incrementSummaryCount(ContentPipelineManager.CONTENT_COUNT, "Content added - " + contentType);
			return true;
		}

		incrementSummaryCount(ContentPipelineManager.CONTENT_COUNT, "Content not added - " + contentType);
		if (!externalConceptMap.containsKey(externalIdentifier)) {
			incrementSummaryCount("Missing External Identifier","Identifier not found in source file - " + externalIdentifier);
		} else if (externalConceptMap.get(externalIdentifier).isHighestUsage() && templatedConcept != null) {
			//Templates that come back as null will already have been counted as out of scope
			incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS,"Highest Usage Mapping Failure");
			report(getTab(ContentPipeLineConstants.TAB_ITEMS_OF_INTEREST), "Highest Usage Mapping Failure", externalIdentifier);
		}
		return false;
	}

	protected void validateTemplatedConcept(String externalIdentifier, TemplatedConcept templatedConcept) throws TermServerScriptException {
		if (templatedConcept == null || templatedConcept.getConcept() == null) {
			if (externalConceptMap.get(externalIdentifier) == null) {
				report(getTab(TAB_MODELING_ISSUES),
						externalIdentifier,
						ContentPipelineManager.getSpecialInterestIndicator(externalIdentifier),
						"N/A",
						"Critical: External identifier not found in external concept map");
			} else {
				report(getTab(TAB_MODELING_ISSUES),
						externalIdentifier,
						ContentPipelineManager.getSpecialInterestIndicator(externalIdentifier),
						externalConceptMap.get(externalIdentifier).getLongDisplayName(),
						"Concept not created");
			}
			return;
		}

		ExternalConcept externalConcept = templatedConcept.getExternalConcept();
		Concept concept = templatedConcept.getConcept();

		if (templatedConcept instanceof TemplatedConceptNull) {
			report(getTab(TAB_MODELING_ISSUES),
					externalConcept.getExternalIdentifier(),
					ContentPipelineManager.getSpecialInterestIndicator(externalConcept.getExternalIdentifier()),
					externalConcept.getLongDisplayName(),
					"Does not meet criteria for template match",
					"Property: " + externalConcept.getProperty());
		} else {
			String fsn = concept.getFsn();
			boolean insufficientTermPopulation = fsn.contains("[");
			//Some panels have words like '[Moles/volume]' in them, so check also for slot token names (all caps).  Not Great.
			if (insufficientTermPopulation && hasAllCapsSlot(fsn)) {
				concept.addIssue(FSN_FAILURE + " to populate required slot: " + fsn);
				templatedConcept.addProcessingFlag(ProcessingFlag.DROP_OUT);
			}

			if (concept.hasIssues() ) {
				concept.addIssue("Template used: " + templatedConcept.getClass().getSimpleName());
				report(getTab(TAB_MODELING_ISSUES),
						externalConcept.getExternalIdentifier(),
						ContentPipelineManager.getSpecialInterestIndicator(externalConcept.getExternalIdentifier()),
						externalConcept.getLongDisplayName(),
						templatedConcept.getConcept().getIssues(",\n"));
			}
		}
		flushFilesSoft();
	}
	
	/**
	 * Checks if a string contains tokens enclosed in square brackets that are all in capital letters.
	 *
	 * @param fsn The string to check.
	 * @return true if there is at least one token in all caps within square brackets; false otherwise.
	 */
	private boolean hasAllCapsSlot(String fsn) {
		Matcher matcher = allCapsSlotPattern.matcher(fsn);
		return matcher.find();
	}
	
	protected boolean containsObjectionableWord(ExternalConcept externalConcept) throws TermServerScriptException {
		//Does this LoincNum feature an objectionable word?  Skip if so.
		for (String objectionableWord : getObjectionableWords()) {
			if (externalConcept.getLongDisplayName() == null) {
				LOGGER.debug("Unable to obtain display name for {}", externalConcept.getExternalIdentifier());
			} else if (externalConcept.getLongDisplayName().toLowerCase().contains(" " + objectionableWord + " ")) {
				report(getTab(TAB_MODELING_ISSUES),
						externalConcept.getExternalIdentifier(),
						ContentPipelineManager.getSpecialInterestIndicator( externalConcept.getExternalIdentifier()),
						externalConcept.getLongDisplayName(),
						"Contains objectionable word - " + objectionableWord);
				return true;
			}
		}
		return false;
	}

	protected boolean confirmExternalIdentifierExists(String externalIdentifier) throws TermServerScriptException {
		//Do we have consistency between the detail map and the main loincTermMap?
		if (!externalConceptMap.containsKey(externalIdentifier)) {
			report(getTab(TAB_MODELING_ISSUES),
					externalIdentifier,
					ContentPipelineManager.getSpecialInterestIndicator(externalIdentifier),
					"N/A",
					"Failed integrity. Identifier " + externalIdentifier + " from detail file, not known in main external concept file.");
			return false;
		}
		return true;
	}
	
	
	protected String inScope(String property) throws TermServerScriptException {
		//Construct a dummy LoincNum with this property and see if it's in scope or not
		ExternalConceptNull dummy = new ExternalConceptNull(DUMMY_EXTERNAL_IDENTIFIER, property);
		return getAppropriateTemplate(dummy) instanceof TemplatedConceptNull ? "N" : "Y";
	}

}