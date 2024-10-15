package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LoincTemplatedConcept extends TemplatedConcept implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConcept.class);
	private static final int GROUP_1 = 1;
	protected static final String SEM_TAG = " (observable entity)";

	private static final Set<String> skipSlotTermMapPopulation = new HashSet<>(Arrays.asList("PROPERTY", "COMPONENT", "DIVISORS"));

	private static final Map<String, String> mapTypeToPrimaryColumn = new HashMap<>();
	static {
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_ADJUSTMENT, "COMPSUBPART3_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_CHALLENGE, "COMPSUBPART2_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_CLASS, "CLASS_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_COMPONENT, "COMPNUM_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_COUNT, "COMPSUBPART4_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_DIVISORS, "COMPDENOM_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_METHOD, "METHOD_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_PROPERTY, "PROPERTYMIXEDCASE_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_SCALE, "SCALE_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_SUFFIX, "COMPNUMSUFFIX_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_SUPER_SYSTEM, "SYSTEMSUPERSYSTEM_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_SYSTEM, "SYSTEM_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_TIME_MODIFIER, "TIMEMODIFIER_PN");
		mapTypeToPrimaryColumn.put(LOINC_PART_TYPE_TIME, "TIMECORE_PN");
	}
	
	protected static AttributePartMapManager attributePartMapManager;
	protected static Map<String, LoincTerm> loincNumToLoincTermMap = new HashMap<>();
	protected static RelationshipTemplate percentAttribute;
	
	protected static Map<String, String> termTweakingMap = new HashMap<>();
	//Note that the order in which matches are done is important - search for the longest strings first.   So use List rather than Set
	protected static Map<Concept, List<String>> typeValueTermRemovalMap = new HashMap<>();
	protected static Map<String, List<String>> valueSemTagTermRemovalMap = new HashMap<>();
	protected static Concept conceptModelObjectAttrib;
	protected static Concept precondition;
	protected static Concept relativeTo;
	protected static Set<String> skipPartTypes = new HashSet<>(Arrays.asList("CLASS", "SUFFIX", "DIVISORS", "SUPER SYSTEM", "ADJUSTMENT", "COUNT"));
	protected static Set<String> useTypesInPrimitive = new HashSet<>(Arrays.asList("SYSTEM", "METHOD", "SCALE", "TIME"));
	protected static Set<String> skipLDTColumnNames = new HashSet<>(Arrays.asList("SYSTEMCORE_PN"));
	protected static Set<String> unknownIndicators = new HashSet<>(Arrays.asList("unidentified", "other", "NOS", "unk sub", "unknown", "unspecified", "abnormal"));
	protected static Map<String, LoincUsage> unmappedPartUsageMap = new HashMap<>();
	protected static Map<String, LoincPart> loincParts;
	protected static Set<String> allowSpecimenTermForLoincParts = new HashSet<>(Arrays.asList("LP7593-9", "LP7735-6", "LP189538-4"));
	
	//Map of LoincNums to ldtColumnNames to details
	protected static Map<String, Map<String, LoincDetail>> loincDetailMap;
	protected Set<ProcessingFlag> processingFlags = new HashSet<>();
	
	protected Map<String, Concept> typeMap = new HashMap<>();
	protected String preferredTermTemplate;
	protected Concept concept;
	
	protected Map<String, String> slotTermMap = new HashMap<>();
	protected Map<String, String> slotTermAppendMap = new HashMap<>();
	
	public static void initialise(ContentPipelineManager cpm, GraphLoader gl,
								  AttributePartMapManager attributePartMapManager,
								  Map<String, LoincTerm> loincNumToLoincTermMap,
								  Map<String, Map<String, LoincDetail>> loincDetailMap,
								  Map<String, LoincPart> loincParts) throws TermServerScriptException {
		TemplatedConcept.cpm = cpm;
		TemplatedConcept.gl = gl;
		LoincTemplatedConcept.attributePartMapManager = attributePartMapManager;
		LoincTemplatedConcept.loincNumToLoincTermMap = loincNumToLoincTermMap;
		LoincTemplatedConcept.loincParts = loincParts;

		termTweakingMap.put("702873001", "calculation"); // 702873001 |Calculation technique (qualifier value)|
		termTweakingMap.put("123029007", "point in time"); // 123029007 |Single point in time (qualifier value)|
		termTweakingMap.put("734842000", "source"); //734842000 |Source (property) (qualifier value)|
		termTweakingMap.put("718500008", "excretion"); //718500008 |Excretory process (qualifier value)|
		termTweakingMap.put("4421005", "cell"); //4421005 |Cell structure (cell structure)|
		
		//Populate removals into specific maps depending on how that removal will be processed.
		List<String> removals = Arrays.asList("submitted as specimen", "specimen", "structure", "of", "at", "from");
		typeValueTermRemovalMap.put(DIRECT_SITE, removals);
		
		removals = Arrays.asList("technique");
		typeValueTermRemovalMap.put(TECHNIQUE, removals);

		removals = Arrays.asList("calculation");
		typeValueTermRemovalMap.put(COMPONENT, removals);

		removals = Arrays.asList("(property)");
		typeValueTermRemovalMap.put(PROPERTY_ATTRIB, removals);


		removals = Arrays.asList("clade", "class", "division", "domain", "family", "genus",
				"infraclass", "infraclass", "infrakingdom", "infraorder", "infraorder", "kingdom", "order", 
				"phylum", "species", "subclass", "subdivision", "subfamily", "subgenus", "subkingdom", 
				"suborder", "subphylum", "subspecies", "superclass", "superdivision", "superfamily", 
				"superkingdom", "superorder", "superphylum");
		valueSemTagTermRemovalMap.put("(organism)", removals);
		
		removals = Arrays.asList("population of all", "in portion of fluid");
		valueSemTagTermRemovalMap.put("(body structure)", removals);
		
		LoincTemplatedConcept.loincDetailMap = loincDetailMap;
		
		percentAttribute = new RelationshipTemplate(gl.getConcept("246514001 |Units|"),
				gl.getConcept("415067009 |Percentage unit|"));
		conceptModelObjectAttrib = gl.getConcept("762705008 |Concept model object attribute|");
		precondition = gl.getConcept("704326004 |Precondition (attribute)|");
		relativeTo = gl.getConcept("704325000 |Relative to (attribute)|");
	}

	protected LoincTemplatedConcept(String loincNum) {
		super(loincNum);
	}

	@Override
	public boolean isHighUsage() {
		return loincNumToLoincTermMap.get(externalIdentifier).isHighUsage();
	}

	public boolean isHighestUsage() {
		return loincNumToLoincTermMap.get(externalIdentifier).isHighestUsage();
	}

	protected void applyTemplateSpecificRules(List<RelationshipTemplate> attributes, LoincDetail loincDetail, RelationshipTemplate rt) throws TermServerScriptException {
		//Rules that apply to all templates:
		if (loincDetail.getLoincNum().equals("LP36683-8")) {
			//One off rule for ABO & Rh group
			processingFlags.add(ProcessingFlag.SPLIT_TO_GROUP_PER_COMPONENT);
		}

		//If the value concept in LP map file is to a concept < 49062001 |Device (physical object)|
		// in which case “LOINC Method -> SNOMED 424226004 |Using device (attribute)|.
		if (gl.getDescendantsCache().getDescendants(DEVICE).contains(rt.getTarget())) {
			rt.setType(USING_DEVICE);
		}
	}

	protected void applyTemplateSpecificRules(Description d) {
		//Do we need to apply any specific rules to the description?
		//Override this function if so
	}
	
	protected int getTab(String tabName) throws TermServerScriptException {
		return cpm.getTab(tabName);
	}

	public static LoincTemplatedConcept getAppropriateTemplate(String loincNum, Map<String, LoincDetail> loincDetailMap) throws TermServerScriptException {
		//Keep this method separate, so LoincScript can directly call the one below with a dummy object
		LoincDetail loincDetail = getPartDetail(loincNum, loincDetailMap, "PROPERTY");
		String property = loincDetail.getPartName();
		return getAppropriateTemplate(loincNum, property);
	}

	public static LoincTemplatedConcept getAppropriateTemplate(String loincNum, String property) throws TermServerScriptException {
		return switch (property) {
			case "NFr", "MFr", "CFr", "AFr", "VFr", "SFr" -> LoincTemplatedConceptWithRelative.create(loincNum);
			case "ACnc", "Angle", "CCnc", "CCnt", "Diam", "LaCnc", "LnCnc", "LsCnc", "MCnc", "MCnt", "MoM", "NCnc",
			     "Naric", "PPres", "PrThr", "SCnc", "SCnt", "Titr", "Visc" ->
					LoincTemplatedConceptWithComponent.create(loincNum);
			case "Anat", "Aper", "EntVol", "ID", "Morph", "Prid", "Temp", "Type", "Vol" ->
					LoincTemplatedConceptWithInheres.create(loincNum);
			case "Susc" -> LoincTemplatedConceptWithSusceptibility.create(loincNum);
			case "MRat", "SRat", "VRat", "Vel", "CRat", "ArVRat" -> LoincTemplatedConceptWithProcess.create(loincNum);
			case "MRto", "Ratio", "SRto" -> LoincTemplatedConceptWithRatio.create(loincNum);
			default -> null;
		};
	}


	public static LoincTemplatedConcept populateTemplate(String loincNum, Map<String, LoincDetail> details) throws TermServerScriptException {
		if (loincNum.equals("1882-0")) {
			LOGGER.debug("Check inactivation");
		}
		
		LoincTemplatedConcept templatedConcept = getAppropriateTemplate(loincNum, details);
		if (templatedConcept != null) {
			templatedConcept.populateTemplate(details);
		} else if (loincNumToLoincTermMap.get(loincNum).isHighestUsage()) {
			//This is a highest usage term which is out of scope
			cpm.incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS, "Highest Usage Out of Scope");
		}
		return templatedConcept;
	}

	private  void populateTemplate( Map<String, LoincDetail> details) throws TermServerScriptException {
		//This loinc term is in scope.
		if (isHighestUsage()) {
			cpm.incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS,"Highest Usage In Scope");
		}
		populateParts(details);
		populateTerms();
		if (detailsIndicatePrimitiveConcept() ||
				hasProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE)) {
			getConcept().setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		}

		if (hasProcessingFlag(ProcessingFlag.SPLIT_TO_GROUP_PER_COMPONENT)) {
			splitComponentsIntoDistinctGroups();
		}
		getConcept().addAlternateIdentifier(externalIdentifier, SCTID_LOINC_CODE_SYSTEM);
	}

	public boolean hasProcessingFlag(ProcessingFlag flag) {
		return processingFlags.contains(flag);
	}

	private void populateTerms() throws TermServerScriptException {
		//Start with the template PT and swap out as many parts as we come across
		String ptTemplateStr = preferredTermTemplate;
		
		ptTemplateStr = populateTermTemplateFromSlots(ptTemplateStr);
		ptTemplateStr = tidyUpTerm(ptTemplateStr);
		ptTemplateStr = StringUtils.capitalizeFirstLetter(ptTemplateStr);

		Description pt = Description.withDefaults(ptTemplateStr, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		applyTemplateSpecificRules(pt);

		Description fsn = Description.withDefaults(ptTemplateStr + SEM_TAG, DescriptionType.FSN, Acceptability.PREFERRED);
		applyTemplateSpecificRules(fsn);

		//Also add the Long Common Name as a Synonym
		String lcnStr = loincNumToLoincTermMap.get(externalIdentifier).getLongCommonName();
		Description lcn = Description.withDefaults(lcnStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		//Override the case significance for these
		lcn.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		
		//Add in the traditional colon form that we've previously used as the FSN
		String colonStr = loincNumToLoincTermMap.get(externalIdentifier).getColonizedTerm();
		Description colonDesc = Description.withDefaults(colonStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		colonDesc.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);

		concept.addDescription(pt);
		concept.addDescription(fsn);
		concept.addDescription(lcn);
		concept.addDescription(colonDesc);
	}

	private String populateTermTemplateFromSlots(String ptTemplateStr) {
		//Do we have any slots left to fill?  Find their attribute types via the slot map
		String [] templateItems = org.apache.commons.lang3.StringUtils.substringsBetween(ptTemplateStr,"[", "]");
		if (templateItems == null) {
			return ptTemplateStr;
		}
		
		for (String templateItem : templateItems) {
			String regex = "\\[" + templateItem + "\\]";
			if (templateItem.equals(LOINC_PART_TYPE_METHOD) && hasProcessingFlag(ProcessingFlag.SUPPRESS_METHOD_TERM)) {
				ptTemplateStr = ptTemplateStr.replaceAll(regex, "")
						.replace(" by ", "");
			} else if (slotTermMap.containsKey(templateItem)) {
				String itemStr = StringUtils.decapitalizeFirstLetter(slotTermMap.get(templateItem));
				ptTemplateStr = ptTemplateStr.replaceAll(regex, itemStr);
				//Did we just wipe out a value?  Trim any trailing connecting words like 'at [TIME]' if so
				if (StringUtils.isEmpty(itemStr) && ptTemplateStr.contains(" at ")) {
					ptTemplateStr = ptTemplateStr.replace(" at ", "");
				}
			} else {
				Concept attributeType = typeMap.get(templateItem);
				if (attributeType == null) {
					concept.addIssue("Token " + templateItem + " missing from typeMap in " + this.getClass().getSimpleName());
					return ptTemplateStr;
				}
				Set<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
				if (rels.isEmpty()) {
					continue;
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
			}
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
			if (targetPt.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) ||
					targetPt.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
				itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
			}
			return itemStr;
		} catch (TermServerScriptException e) {
			LOGGER.error("Failed to get term for {}",rt.getTarget().getFsn(),e);
			return rt.getTarget().getFsn();
		}
	}

	private String populateTermTemplate(String itemStr, String templateItem, String ptStr, String partTypeName) {
		//Do we need to append any values to this term
		if (slotTermAppendMap.containsKey(partTypeName)) {
			itemStr += " " + slotTermAppendMap.get(partTypeName);
		}

		ptStr = ptStr.replaceAll(templateItem, itemStr);
		return ptStr;
	}

	private String applyTermTweaking(IRelationship r, String term) {
		Concept value = r.getTarget();
		
		//Firstly, do we have a flat out replacement for this value?
		if (termTweakingMap.containsKey(value.getId())) {
			term = termTweakingMap.get(value.getId());
		}
		
		//Are we making any removals based on semantic tag?
		String semTag = SnomedUtils.deconstructFSN(value.getFsn())[1];
		if (valueSemTagTermRemovalMap.containsKey(semTag)) {
			for (String removal : valueSemTagTermRemovalMap.get(semTag)) {
				term = term.replaceAll(removal, "");
				term = term.replaceAll(StringUtils.capitalizeFirstLetter(removal), "");
			}
		}
		
		//Are we making any removals based on the type?
		if (typeValueTermRemovalMap.containsKey(r.getType())) {
			//Add a space to ensure we do whole word removal
			term = " " + term + " ";
			for (String removal : typeValueTermRemovalMap.get(r.getType())) {
				//Rule 2a. We sometimes allow 'specimen' to be used, for certain loinc parts
				if (removal.equals("specimen") && hasProcessingFlag(ProcessingFlag.ALLOW_SPECIMEN)) {
					continue;
				}
				String removalWithSpaces = " " + removal + " ";
				term = term.replaceAll(removalWithSpaces, " ");
				removalWithSpaces = " " + StringUtils.capitalizeFirstLetter(removal) + " ";
				term = term.replaceAll(removalWithSpaces, " ");
			}
			term = term.trim();
		}
		
		return term;
	}

	protected String tidyUpTerm(String term) {
		term = replaceAndWarn(term, " at [TIME]");
		term = replaceAndWarn(term, " by [METHOD]");
		term = replaceAndWarn(term, " in [SYSTEM]");
		term = replaceAndWarn(term, " using [DEVICE]");
		term = replaceAndWarn(term, " [CHALLENGE]");
		term = term.replaceAll(" {2}", " ");
		return term;
	}

	private String replaceAndWarn(String term, String str) {
		if (term.contains(str)) {
			//Need to make string regex safe
			str = str.replaceAll("\\[","\\\\\\[").replaceAll("\\]","\\\\\\]");
			term = term.replaceAll(str, "");
		}
		return term;
	}

	private void populateParts(Map<String, LoincDetail> details) throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(SCTID_LOINC_EXTENSION_MODULE);
		concept.addRelationship(IS_A, getParentConceptForTemplate());
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
		Set<String> partTypeSeen = new HashSet<>();
		for (LoincDetail loincDetail : details.values()) {
			populatePart(loincDetail, partTypeSeen);
		}
		
		//Ensure attributes are unique (considering both type and value)
		checkAndRemoveDuplicateAttributes();
		
		if (concept.hasIssues()) {
			concept.addIssue("Template used: " + this.getClass().getSimpleName());
		}
	}

	private void populatePart(LoincDetail loincDetail, Set<String> partTypeSeen) throws TermServerScriptException {
		String partTypeName = loincDetail.getPartTypeName();
		if (skipPartTypes.contains(partTypeName)
				|| partTypeSeen.contains(partTypeName)
				|| skipLDTColumnNames.contains(loincDetail.getLDTColumnName())
		) {
			return;
		}

		boolean expectNullMap = false;
		boolean isComponent = partTypeName.equals("COMPONENT");
		List<RelationshipTemplate> attributesToAdd = new ArrayList<>();
		if (isComponent) {
			//We're only going to process the COMPNUM as that the mapping we're really interested in.
			if (!loincDetail.getLDTColumnName().equals(COMPNUM_PN)) {
				return;
			}
			attributesToAdd = determineComponentAttributes();
		} else {
			boolean attributeAdded = addAttributeFromDetail(attributesToAdd, loincDetail);
			//Now if we didn't find a map, then for non-critical parts, we'll used the loinc part name anyway
			if (!attributeAdded && useTypesInPrimitive.contains(loincDetail.getPartTypeName())) {
				if (loincDetail.getPartNumber().equals(LoincScript.LOINC_TIME_PART)) {
					expectNullMap = true;
				} else {
					slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
					processingFlags.add(ProcessingFlag.MARK_AS_PRIMITIVE);
				}
			}
		}

		//"Unknown" type parts should use the loinc description rather than the concept
		if (containsUnknownPhrase(loincDetail.getPartName())) {
			slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
			processingFlags.add(ProcessingFlag.MARK_AS_PRIMITIVE);
		}

		partTypeSeen.add(partTypeName);

		for (RelationshipTemplate rt : attributesToAdd) {
			addAttributesToConcept(rt, loincDetail, expectNullMap);
		}
	}

	protected Concept getParentConceptForTemplate() throws TermServerScriptException {
		//Only Ratio template need to override this, for now
		return OBSERVABLE_ENTITY;
	}

	private void addAttributesToConcept(RelationshipTemplate rt, LoincDetail loincDetail, boolean expectNullMap) throws TermServerScriptException {
		if (rt != null) {
			mapped++;
			concept.addRelationship(rt, GROUP_1);
		} else if (!expectNullMap){
			unmapped++;
			String issue = "Not Mapped - " + loincDetail.getPartTypeName() + " | " + loincDetail.getPartNumber() + "| " + loincDetail.getPartName();
			concept.addIssue(issue);
			concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			partNumsUnmapped.add(loincDetail.getPartNumber());
			((LoincScript)cpm).addMissingMapping(loincDetail.getPartNumber(), loincDetail.getLoincNum());

			//Record the fact that we failed to find a map on a per part basis
			partNumsMapped.add(loincDetail.getPartNumber());
			LoincUsage usage = unmappedPartUsageMap.get(loincDetail.getPartNumber());
			if (usage == null) {
				usage = new LoincUsage();
				unmappedPartUsageMap.put(loincDetail.getPartNumber(), usage);
			}
			usage.add(loincNumToLoincTermMap.get(loincDetail.getLoincNum()));
		}
	}

	private boolean containsUnknownPhrase(String partName) {
		partName = partName.toLowerCase();
		for (String wordIndicatingUnknown : unknownIndicators){
			if (partName.contains(wordIndicatingUnknown)) {
				return true;
			}
		}
		return false;
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

	protected abstract List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException;

	//TO DO This function is a problem because we can have more than one detail with the same partType
	//Method below is better because it first looks up the primary column name for that part type
	private static LoincDetail getPartDetail(String loincNum, Map<String, LoincDetail> loincDetailMap, String partTypeName) throws TermServerScriptException {
		for (LoincDetail detail : loincDetailMap.values()) {
			if (detail.getPartTypeName().equals(partTypeName)) {
				return detail;
			}
		}
		throw new TermServerScriptException("Unable to find part " + partTypeName + " in " + loincNum);
	}


	protected LoincDetail getLoincDetailForPartType(String typeName) throws TermServerScriptException {
		String primaryColumnName = mapTypeToPrimaryColumn.get(typeName);
		return getLoincDetailOrThrow(primaryColumnName);
	}
	
	public void setConcept(Concept c) {
		this.concept = c;
	}

	public Concept getConcept() {
		return concept;
	}
	
	public String getExternalIdentifier() {
		return externalIdentifier;
	}
	
	public String getWrappedId() {
		return externalIdentifier;
	}

	/**
	 * If the “PartNumber” and “Part” fields (column B and D) are the same for COMPONENT_PN and COMPNUM
	 * @throws TermServerScriptException 
	 */
	protected boolean hasNoSubParts() throws TermServerScriptException {
		LoincDetail ldComponentPn = getLoincDetailOrThrow(COMPONENT_PN);
		LoincDetail ldCompNum = getLoincDetailOrThrow(COMPNUM_PN);

		return ldComponentPn.getPartNumber().equals(ldCompNum.getPartNumber()) &&
				ldComponentPn.getPartName().equals(ldCompNum.getPartName());
	}

	protected boolean hasDetail(String ldtColumnName) {
		//Does this LoincNum feature this particular detail
		Map<String, LoincDetail> partDetailMap = loincDetailMap.get(externalIdentifier);
		return partDetailMap.containsKey(ldtColumnName);
	}

	protected LoincDetail getLoincDetailIfPresent(String ldtColumnName) throws TermServerScriptException {
		//What LoincDetails do we have for this loincNum?
		Map<String, LoincDetail> partDetailMap = loincDetailMap.get(externalIdentifier);
		if (partDetailMap == null) {
			throw new TermServerScriptException("No LOINC part details found for loincNum: " + externalIdentifier);
		}
		return partDetailMap.get(ldtColumnName);
	}

	protected LoincDetail getLoincDetailOrThrow(String ldtColumnName) throws TermServerScriptException {
		LoincDetail loincDetail = getLoincDetailIfPresent(ldtColumnName);
		if (loincDetail == null) {
			throw new TermServerScriptException("Could not find detail with ldtColumnName " + ldtColumnName + " for loincNum " + externalIdentifier);
		}
		return loincDetail;
	}

	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return (detailPresent(COMPSUBPART3_PN) || detailPresent(COMPSUBPART4_PN));
	}
	
	protected boolean detailPresent(String ldtColumnName) throws TermServerScriptException {
		return getLoincDetailIfPresent(ldtColumnName) != null;
	}

	protected boolean addAttributeFromDetail(List<RelationshipTemplate> attributes, LoincDetail loincDetail) throws TermServerScriptException {
		//Now given the template that we've chosen for this LOINC Term, what attribute
		//type would we use?
		Concept attributeType = typeMap.get(loincDetail.getPartTypeName());
		if (attributeType == null) {
			cpm.report(getTab(TAB_MODELING_ISSUES),
					externalIdentifier,
					ContentPipelineManager.getSpecialInterestIndicator(externalIdentifier),
					loincDetail.getPartNumber(),
					"Type in context not identified - " + loincDetail.getPartTypeName() + " | " + this.getClass().getSimpleName(),
					loincDetail.getPartName());
			return false;
		}
		return addAttributeFromDetailWithType(attributes, loincDetail, attributeType);
	}

	protected boolean addAttributeFromDetailWithType(List<RelationshipTemplate> attributes, LoincDetail loincDetail, Concept attributeType) throws TermServerScriptException {
		try {
			String loincNum = externalIdentifier;
			String loincPartNum = loincDetail.getPartNumber();

			if (loincDetail.getPartTypeName().contentEquals("SYSTEM") && allowSpecimenTermForLoincParts.contains(loincDetail.getPartNumber())) {
				processingFlags.add(ProcessingFlag.ALLOW_SPECIMEN);
			}

			List<RelationshipTemplate> additionalAttributes = attributePartMapManager.getPartMappedAttributeForType(getTab(TAB_MODELING_ISSUES), loincNum, loincPartNum, attributeType);

			if (additionalAttributes.isEmpty()) {
				if (loincDetail.getPartNumber().equals(LoincScript.LOINC_TIME_PART)) {
					//Rule xi if we have a time, then we don't need to populate that field
					slotTermMap.put(loincDetail.getPartTypeName(), "");
				} else if (!skipSlotTermMapPopulation.contains(loincDetail.getPartTypeName())) {
					//Rule 2.c  If we don't have a part mapping, use what we do get in the FSN
					slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
				} else {
					throw new TermServerScriptException("Unable to find appropriate attribute mapping for " + loincNum + " / " + loincPartNum + " (" + loincDetail.getLDTColumnName() + ") - " + loincDetail.getPartName());
				}
			}

			attributes.addAll(additionalAttributes);
			for (RelationshipTemplate rt : additionalAttributes) {
				applyTemplateSpecificRules(attributes, loincDetail, rt);
			}
			return !additionalAttributes.isEmpty();
		} catch (TermServerScriptException e) {
			//If we've not found the COMPNUM_PN then we're not going to go ahead with this Loinc Term
			if (loincDetail.getLDTColumnName().equals("COMPNUM_PN")) {
				concept.addIssue(e.getMessage());
				processingFlags.add(ProcessingFlag.DROP_OUT);
			} else {
				concept.addIssue(e.getMessage() + " definition status set to Primitive");
				this.concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			}
		}
		return false;
	}

	public String toString() {
		return this.getClass().getSimpleName() + " for loincNum " + externalIdentifier;
	}

	protected void processSubComponents(List<RelationshipTemplate> attributes, Concept componentAttribType) throws TermServerScriptException {
		if (detailPresent(COMPSUBPART2_PN)) {
			if(attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
			}
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPSUBPART2_PN), precondition);
		}

		if (detailPresent(COMPSUBPART3_PN)) {
			if (attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
			}
			LoincDetail componentDetail = getLoincDetailOrThrow(COMPSUBPART3_PN);
			slotTermAppendMap.put(LOINC_PART_TYPE_COMPONENT, componentDetail.getPartName());
			processingFlags.add(ProcessingFlag.MARK_AS_PRIMITIVE);
		}

		if (detailPresent(COMPSUBPART4_PN)) {
			if (attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
			}
			LoincDetail componentDetail = getLoincDetailOrThrow(COMPSUBPART4_PN);
			slotTermAppendMap.put(LOINC_PART_TYPE_COMPONENT, componentDetail.getPartName());
			processingFlags.add(ProcessingFlag.MARK_AS_PRIMITIVE);
		}
	}

	protected void populateTypeMapCommonItems() throws TermServerScriptException {
		typeMap.put(LOINC_PART_TYPE_CHALLENGE, precondition);
		typeMap.put(LOINC_PART_TYPE_COMPONENT, gl.getConcept("246093002 |Component (attribute)|"));
		typeMap.put(LOINC_PART_TYPE_DEVICE, gl.getConcept("424226004 |Using device (attribute)|"));
		typeMap.put(LOINC_PART_TYPE_METHOD, gl.getConcept("246501002 |Technique (attribute)|"));
		typeMap.put(LOINC_PART_TYPE_PROPERTY, gl.getConcept("370130000 |Property (attribute)|"));
		typeMap.put(LOINC_PART_TYPE_SCALE, gl.getConcept("370132008 |Scale type (attribute)|"));
		typeMap.put(LOINC_PART_TYPE_SYSTEM, gl.getConcept("704327008 |Direct site (attribute)|"));
		typeMap.put(LOINC_PART_TYPE_TIME, gl.getConcept("370134009 |Time aspect (attribute)|"));
	}

	protected boolean compNumPartNameAcceptable(List<RelationshipTemplate> attributes) throws TermServerScriptException {
		//We can't yet deal with "given"
		if (detailPresent(COMPNUM_PN) &&
				getLoincDetailOrThrow(COMPNUM_PN).getPartName().endsWith(" given")) {
			String issue = "Skipping concept using 'given'";
			concept.addIssue(issue);
			attributes.add(null);
			return false;
		}
		return true;
	}

	private void splitComponentsIntoDistinctGroups() {
		//Split out the components from the other attributes, then copy all the non-component values
		//into their own groups
		List<Relationship> componentAttributes = new ArrayList<>();
		List<Relationship> otherAttributes = new ArrayList<>();
		Set<Relationship> initialRelationships = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Relationship r : initialRelationships) {
			if (r.getType().equals(COMPONENT)) {
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
}
