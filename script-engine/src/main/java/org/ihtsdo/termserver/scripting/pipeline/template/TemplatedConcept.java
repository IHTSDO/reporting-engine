package org.ihtsdo.termserver.scripting.pipeline.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.ConceptWrapper;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConceptUsage;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public abstract class TemplatedConcept implements ScriptConstants, ConceptWrapper, ContentPipeLineConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplatedConcept.class);

	public enum IterationIndicator { NEW, REMOVED, RESURRECTED, MODIFIED, UNCHANGED, MANUAL, REMAINS_INACTIVE }

	protected static ContentPipelineManager cpm;
	protected static GraphLoader gl;

	protected static Map<String, Acceptability> defaultPrefAcceptabilityMap;
	protected static Map<String, Acceptability> defaultAccAcceptabilityMap;

	protected static Set<String> partNumsMapped = new HashSet<>();
	protected static Set<String> partNumsUnmapped = new HashSet<>();
	protected static Map<String, ExternalConceptUsage> unmappedPartUsageMap = new HashMap<>();

	protected Concept existingConcept = null;
	protected boolean existingConceptHasInactivations = false;

	protected List<String> differencesFromExistingConcept = new ArrayList<>();

	protected IterationIndicator iterationIndicator;

	protected ExternalConcept externalConcept;
	protected Concept concept;
	
	private Set<ProcessingFlag> processingFlags = new HashSet<>();
	
	protected Map<String, Concept> typeMap = new HashMap<>();
	private String preferredTermTemplate;
	
	protected Map<String, String> slotTermMap = new HashMap<>();
	protected Map<String, String> slotTermAppendMap = new HashMap<>();

	public static void initialise(ContentPipelineManager cpm) throws TermServerScriptException {
		TemplatedConcept.cpm = cpm;
		TemplatedConcept.gl = cpm.getGraphLoader();

		//Most products we're working with will only support en-US
		defaultPrefAcceptabilityMap = Map.of(
				US_ENG_LANG_REFSET, Acceptability.PREFERRED
		);

		defaultAccAcceptabilityMap = Map.of(
				US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE
		);
	}

	protected TemplatedConcept(ExternalConcept externalConcept) {
		this.externalConcept = externalConcept;
	}

	public static void reportStats(int tabIdx) throws TermServerScriptException {
		cpm.report(tabIdx, "PartNum Mapping");
		cpm.report(tabIdx, "", "Unique PartNums mapped", partNumsMapped.size());
		cpm.report(tabIdx, "","Unique PartNums unmapped", partNumsUnmapped.size());
	}

	public ExternalConcept getExternalConcept() {
		return externalConcept;
	}

	public String getExternalIdentifier() {
		return externalConcept.getExternalIdentifier();
	}

	public boolean isHighUsage() {
		return externalConcept.isHighUsage();
	}

	public boolean isHighestUsage() {
		return externalConcept.isHighestUsage();
	}
	
	public IterationIndicator getIterationIndicator() {
		return iterationIndicator;
	}
	
	protected abstract String getSemTag();

	public void setIterationIndicator(IterationIndicator iterationIndicator) {
		this.iterationIndicator = iterationIndicator;
	}

	public Concept getExistingConcept() {
		return existingConcept;
	}

	public void setExistingConcept(Concept existingConcept) {
		this.existingConcept = existingConcept;
	}

	public void populateAlternateIdentifier() {
		if (!(this instanceof TemplatedConceptNull)
				&& !getConcept().hasAlternateIdentifier(getSchemaId())) {
			getConcept().addAlternateIdentifier(getExternalIdentifier(), getSchemaId());
		}
	}

	protected void prepareConceptDefaultedForModule(String moduleId) throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(moduleId);
		concept.addRelationship(IS_A, getParentConceptForTemplate());
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
	}

	public String getDifferencesFromExistingConceptWithMultiples() {
		//Count instances of each difference and add (x N) to the string form for each where N > 1
		Map<String, Integer> differenceCounts = new HashMap<>();
		for (String difference : differencesFromExistingConcept) {
			differenceCounts.merge(difference, 1, Integer::sum);
		}
		return differenceCounts.entrySet().stream()
				.map(e -> e.getKey() + (e.getValue() > 1 ? " (x " + e.getValue() + ")" : ""))
				.collect(Collectors.joining(",\n"));
	}

	public void addDifferenceFromExistingConcept(String differenceFromExistingConcept) {
		this.differencesFromExistingConcept.add(differenceFromExistingConcept);
	}

	public boolean existingConceptHasInactivations() {
		return existingConceptHasInactivations;
	}

	public void setExistingConceptHasInactivations(boolean existingConceptHasInactivations) {
		this.existingConceptHasInactivations = existingConceptHasInactivations;
	}

	protected String bracket(String str) {
		return "[" + str + "]";
	}

	@Override
	public String getWrappedId() {
		return getConcept().getId();
	}

	@Override
	public Concept getConcept() {
		return concept;
	}
	
	@Override
	public void setConcept(Concept concept) {
		this.concept = concept;
	}
	
	public void addProcessingFlag(ProcessingFlag flag) {
		processingFlags.add(flag);
	}
	
	public boolean hasProcessingFlag(ProcessingFlag flag) {
		return processingFlags.contains(flag);
	}

	public void populateTemplate() throws TermServerScriptException {
		if (isHighUsage()) {
			cpm.incrementSummaryCount(ContentPipelineManager.HIGH_USAGE_COUNTS, "High Usage In Scope");
			if (isHighestUsage()) {
				cpm.incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS,"Highest Usage In Scope");
			}
		}

		populateParts();
		populateTerms();
		reviewCaseSensitivity(concept);
		if (detailsIndicatePrimitiveConcept() ||
				hasProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE)) {
			getConcept().setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		}

		if (hasProcessingFlag(ProcessingFlag.SPLIT_TO_GROUP_PER_COMPONENT)) {
			splitComponentsIntoDistinctGroups();
		}
	}


	protected boolean addAttributeFromDetail(List<RelationshipTemplate> attributes, Part part) throws TermServerScriptException {
		//Now given the template that we've chosen for this LOINC Term, what attribute
		//type would we use?
		Concept attributeType = typeMap.get(part.getPartTypeName());
		if (attributeType == null) {
			cpm.report(cpm.getTab(TAB_MODELING_ISSUES),
					getExternalIdentifier(),
					ContentPipelineManager.getSpecialInterestIndicator(getExternalIdentifier()),
					part.getPartNumber(),
					"Type in context not identified - " + part.getPartTypeName() + " | " + this.getClass().getSimpleName(),
					part.getPartName());
			return false;
		}
		return addAttributeFromDetailWithType(attributes, part, attributeType);
	}

	protected boolean addAttributeFromDetailWithType(List<RelationshipTemplate> attributes, Part part, Concept attributeType) throws TermServerScriptException {
		List<RelationshipTemplate> additionalAttributes = cpm.getAttributePartManager().getPartMappedAttributeForType(cpm.getTab(TAB_MODELING_ISSUES), getExternalIdentifier(), part.getPartNumber(), attributeType);
		for (RelationshipTemplate rt : additionalAttributes) {
			applyTemplateSpecificModellingRules(additionalAttributes, part, rt);
		}
		attributes.addAll(additionalAttributes);
		return !additionalAttributes.isEmpty();
	}

	protected abstract void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt)  throws TermServerScriptException;

	protected void addAttributesToConcept(RelationshipTemplate rt, Part part, boolean expectNullMap) {
		if (rt != null) {
			partNumsMapped.add(part.getPartNumber());
			cpm.incrementSummaryCount("Part Mapping Processing", "Mapped successfully");
			concept.addRelationship(rt, GROUP_1);
		} else if (!expectNullMap && !cpm.getMappingsAllowedAbsent().contains(part.getPartNumber())){
			//Record the fact that we failed to find a map on a per-part basis
			partNumsUnmapped.add(part.getPartNumber());

			cpm.incrementSummaryCount("Part Mapping Processing", "Mapped unsuccessfully");
			String issue = "Not Mapped - " + part.getPartTypeName() + " | " + part.getPartNumber() + "| " + part.getPartName();
			concept.addIssue(issue);
			concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			partNumsUnmapped.add(part.getPartNumber());
			if (part instanceof LoincDetail loincDetail) {
				cpm.addMissingMapping(part.getPartNumber(), loincDetail.getLoincNum());
			}

			ExternalConceptUsage usage = unmappedPartUsageMap.get(part.getPartNumber());
			if (usage == null) {
				usage = new ExternalConceptUsage();
				unmappedPartUsageMap.put(part.getPartNumber(), usage);
			}
			usage.add(getExternalConcept());
		}
	}

	public abstract String getSchemaId();

	protected Concept getParentConceptForTemplate() throws TermServerScriptException {
		return OBSERVABLE_ENTITY;
	}

	protected void populateTerms() throws TermServerScriptException {
		//Start with the template PT and swap out as many parts as we come across
		String ptTemplateStr = getPreferredTermTemplate();
		
		ptTemplateStr = populateTermTemplateFromSlots(ptTemplateStr);
		ptTemplateStr = tidyUpTerm(ptTemplateStr);
		ptTemplateStr = StringUtils.capitalizeFirstLetter(ptTemplateStr);

		Description pt = Description.withDefaults(ptTemplateStr, DescriptionType.SYNONYM, defaultPrefAcceptabilityMap);
		applyTemplateSpecificTermingRules(pt);

		Description fsn = Description.withDefaults(ptTemplateStr + getSemTag(), DescriptionType.FSN, defaultPrefAcceptabilityMap);
		applyTemplateSpecificTermingRules(fsn);

		concept.addDescription(pt);
		concept.addDescription(fsn);

		if (cpm.shouldIncludeShortNameDescription()) {
			//Also add the Long Common Name as a Synonym
			String scn = getExternalConcept().getShortDisplayName();
			Description lcn = Description.withDefaults(scn, DescriptionType.SYNONYM, defaultAccAcceptabilityMap);
			//Override the case significance for these
			lcn.addIssue(CaseSensitivityUtils.FORCE_CS);
			concept.addDescription(lcn);
		}
	}

	private void reviewCaseSensitivity(Concept c) throws TermServerScriptException {
		CaseSensitivityUtils csUtils = CaseSensitivityUtils.get();
		for (Description d : c.getDescriptions()) {
			d.setCaseSignificance(csUtils.suggestCorrectCaseSignificance(c, d));
		}
	}

	protected abstract void applyTemplateSpecificTermingRules(Description pt) throws TermServerScriptException;

	protected String populateTermTemplateFromSlots(String ptTemplateStr) throws TermServerScriptException {
		//Do we have any slots left to fill?  Find their attribute types via the slot map
		String [] templateItems = org.apache.commons.lang3.StringUtils.substringsBetween(ptTemplateStr,"[", "]");
		if (templateItems == null) {
			return ptTemplateStr;
		}

		for (String templateItem : templateItems) {
			ptTemplateStr = populateTemplateItem(templateItem, ptTemplateStr);
		}
		return ptTemplateStr;
	}

	protected String populateTemplateItem(String templateItem, String ptTemplateStr) throws TermServerScriptException {
		String regex = "\\[" + templateItem + "\\]";
		if (slotTermMap.containsKey(templateItem)) {
			String itemStr = slotTermMap.get(templateItem);

			if (StringUtils.isEmpty(itemStr)){
				regex = includePrepositionInDeletion(regex, ptTemplateStr);
			} else if (safeToDecapitalizeFirstLetter(itemStr)){
				itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
			}

			ptTemplateStr = ptTemplateStr.replaceAll(regex, itemStr);
		} else {
			ptTemplateStr = populateTermTemplateFromAttribute(regex, templateItem, ptTemplateStr);
		}
		return ptTemplateStr;
	}

	private String includePrepositionInDeletion(String regex, String ptTemplateStr) {
		// Find the position of the regex (slot) in the template string
		int slotIndex = ptTemplateStr.indexOf(regex.replaceAll("\\\\", ""));
		if (slotIndex == -1) {
			return regex; // fallback, not found
		}
		// Find the word (preposition) before the slot
		int preStart = slotIndex - 1;
		while (preStart >= 0 && Character.isWhitespace(ptTemplateStr.charAt(preStart))) {
			preStart--;
		}
		while (preStart >= 0 && Character.isLetter(ptTemplateStr.charAt(preStart))) {
			preStart--;
		}
		preStart++;
		// Extract the preposition and build the new regex
		String preposition = ptTemplateStr.substring(preStart, slotIndex);
		return " " + preposition + regex;
	}

	private boolean safeToDecapitalizeFirstLetter(String phrase) throws TermServerScriptException {
		CaseSensitivityUtils csUtils = CaseSensitivityUtils.get();
		//We're trying to match the previous behaviour of the system to avoid unnecessary capitalization churn.
		//It _looks_ like if the first word contained a capital letter after the first letter, then we previously considered
		//it CS and didn't decapitalize.
		String firstWord = phrase.split(" ")[0];

		if (StringUtils.hasCapitalAfterFirstLetter(firstWord)
				|| csUtils.startsWithKnownCaseSensitiveTerm(getConcept(), phrase)) {
			return false;
		}
		return !csUtils.startsWithAcronym(phrase);
	}

	protected String populateTermTemplate(String itemStr, String templateItem, String ptStr, String partTypeName) {
		//Do we need to append any values to this term
		if (slotTermAppendMap.containsKey(partTypeName)) {
			itemStr += " " + slotTermAppendMap.get(partTypeName);
		}

		ptStr = ptStr.replaceAll(templateItem, itemStr);
		return ptStr;
	}

	protected String populateTermTemplateFromAttribute(String regex, String templateItem, String ptTemplateStr) {
		Concept attributeType = typeMap.get(templateItem);
		if (attributeType == null) {
			concept.addIssue("Token " + templateItem + " missing from typeMap in " + this.getClass().getSimpleName());
			return ptTemplateStr;
		}
		Set<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		if (rels.isEmpty()) {
			return ptTemplateStr;
		}
		if (rels.size() > 1) {
			//Special case for influenza virus antigen
			if (rels.iterator().next().getTarget().getFsn().contains("Influenza")) {
				ptTemplateStr = populateTermTemplate("influenza antibody", regex, ptTemplateStr, templateItem);
			} else {
				String itemStr = rels.stream()
						.map(this::getCaseAdjustedTweakedTerm)
						.collect(Collectors.joining(" and "));
				ptTemplateStr = populateTermTemplate(itemStr, regex, ptTemplateStr, templateItem);
			}
		} else {
			RelationshipTemplate rt = new RelationshipTemplate(rels.iterator().next());
			ptTemplateStr = populateTermTemplate(rt, regex, ptTemplateStr, templateItem);
		}
		return ptTemplateStr;
	}

	private String populateTermTemplate(RelationshipTemplate rt, String templateItem, String ptStr, String partTypeName) {
		String itemStr = getCaseAdjustedTweakedTerm(rt);
		return populateTermTemplate(itemStr, templateItem, ptStr, partTypeName);
	}

	private String getCaseAdjustedTweakedTerm(IRelationship rt) {
		//TO DO Detect GB Spelling and break out another term
		try {
			Description targetPt = rt.getTarget().getPreferredSynonym(US_ENG_LANG_REFSET);
			String itemStr = targetPt.getTerm();
			itemStr = applyTermTweaking(rt, itemStr);

			//Can we make this lower case?
			if (CaseSensitivityUtils.isciorcI(targetPt) && !CaseSensitivityUtils.get().startsWithKnownCaseSensitiveTerm(getConcept(), itemStr)) {
				itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
			}
			return itemStr;
		} catch (TermServerScriptException e) {
			LOGGER.error("Failed to get term for {}",rt.getTarget().getFsn(),e);
			return rt.getTarget().getFsn();
		}
	}

	protected String applyTermTweaking(IRelationship r, String term) {
		return term;  //Override to apply tweaks
	}

	protected abstract void populateParts() throws TermServerScriptException;

	protected abstract boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException;

	private void splitComponentsIntoDistinctGroups() {
		//Split out the components from the other attributes, then copy all the non-component values
		//into their own groups
		List<Relationship> componentAttributes = new ArrayList<>();
		List<Relationship> otherAttributes = new ArrayList<>();
		Set<Relationship> initialRelationships = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Relationship r : initialRelationships) {
			if (r.getType().equals(COMPONENT)
					|| r.getType().equals(INHERES_IN)) {
				componentAttributes.add(r);
				//And remove, we'll add back in later
				concept.removeRelationship(r);
			} else {
				otherAttributes.add(r);
			}
		}

		int groupNum = 0;
		for (Relationship componentAttribute : componentAttributes) {
			groupNum++;
			RelationshipGroup g = concept.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, groupNum);
			if (g == null) {
				g = new RelationshipGroup(groupNum);
			}
			//Add the component attribute if required
			if (!g.containsTypeValue(componentAttribute)) {
				concept.addRelationship(componentAttribute.getType(), componentAttribute.getTarget(), groupNum);
			}
			//Add all the other attributes
			for (Relationship otherAttribute : otherAttributes) {
				if (!g.containsTypeValue(otherAttribute)) {
					concept.addRelationship(otherAttribute.getType(), otherAttribute.getTarget(), groupNum);
				}
			}
		}
	}

	public String getPreferredTermTemplate() {
		return preferredTermTemplate;
	}

	public void setPreferredTermTemplate(String preferredTermTemplate) {
		this.preferredTermTemplate = preferredTermTemplate;
	}

	protected String tidyUpTerm(String term) {
		term = removeUnpopulatedTermSlot(term, " at [TIME]");
		term = removeUnpopulatedTermSlot(term, " by [METHOD]");
		term = removeUnpopulatedTermSlot(term, " in [SYSTEM]");
		term = removeUnpopulatedTermSlot(term, " using [DEVICE]");
		term = removeUnpopulatedTermSlot(term, " [CHALLENGE]");
		term = term.replaceAll(" {2}", " ");
		return term;
	}

	private String removeUnpopulatedTermSlot(String term, String str) {
		if (term.contains(str)) {
			//Need to make string regex safe
			str = str.replaceAll("[\\[\\]]", "\\\\$0");
			term = term.replaceAll(str, "");
		}
		return term;
	}


	protected void checkAndRemoveDuplicateAttributes() {
		Set<RelationshipTemplate> relsSeen = new HashSet<>();
		Set<Relationship> relsToRemove = new HashSet<>();
		for (Relationship r : concept.getRelationships()) {
			RelationshipTemplate rt = new RelationshipTemplate(r);
			if (relsSeen.contains(rt)) {
				relsToRemove.add(r);
			} else {
				relsSeen.add(rt);
			}
		}

		for (Relationship r : relsToRemove) {
			concept.getRelationships().remove(r);
			LOGGER.warn("Removed a redundant {} from {}",r, this);
		}
	}
}
