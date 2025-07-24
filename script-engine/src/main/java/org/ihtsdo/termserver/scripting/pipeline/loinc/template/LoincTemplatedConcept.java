package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.*;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript.LOINC_MEASUREMENT_PART;
import static org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript.LOINC_OBSERVATION_PART;

public abstract class LoincTemplatedConcept extends TemplatedConcept implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConcept.class);

	private static final String SPECIMEN = "specimen";

	private static final Concept AUTOMATED_TECHNIQUE = new Concept("570101010000100", "Automated technique (qualifier value)");

	private static final Set<String> skipSlotTermMapPopulation = new HashSet<>(Arrays.asList(
			LOINC_PART_TYPE_PROPERTY,
			LOINC_PART_TYPE_COMPONENT,
			LOINC_PART_TYPE_DIVISORS));

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
	
	protected static RelationshipTemplate percentAttribute;
	
	protected static Map<String, String> termTweakingMap = new HashMap<>();
	//Note that the order in which matches are done is important - search for the longest strings first.   So use List rather than Set
	protected static Map<Concept, List<String>> typeValueTermRemovalMap = new HashMap<>();
	protected static Map<String, List<String>> valueSemTagTermRemovalMap = new HashMap<>();
	protected static Concept conceptModelObjectAttrib;
	protected static Concept precondition;
	protected static Concept relativeTo;
	protected static Set<String> skipPartTypes = new HashSet<>(Arrays.asList("CLASS", "SUFFIX", "SUPER SYSTEM", "ADJUSTMENT", "COUNT"));
	protected static Set<String> useTypesInPrimitive = new HashSet<>(Arrays.asList("SYSTEM", "METHOD", "SCALE", "TIME"));
	protected static Set<String> skipLDTColumnNames = new HashSet<>(List.of("SYSTEMCORE_PN"));
	protected static Set<String> columnsToCheckForUnknownIndicators = new HashSet<>(Arrays.asList(COMPNUM_PN, COMPDENOM_PN, SYSTEM_PN));
	protected static Set<String> unknownIndicators = new HashSet<>(Arrays.asList("unidentified", "other", "NOS", "unk sub", "unknown", "unspecified", "abnormal", "total"));
	protected static Set<String> allowSpecimenTermForLoincParts = new HashSet<>(Arrays.asList("LP7593-9", "LP7735-6", "LP189538-4"));
	
	//Map of LoincNums to ldtColumnNames to details
	protected static Map<String, Map<String, LoincDetail>> loincDetailMapAllTerms;
	
	//Map of Loinc Details for this conept
	protected Map<String, LoincDetail> loincDetailMap;

	@Override
	public String getSchemaId() {
		return SCTID_LOINC_SCHEMA;
	}

	@Override
	protected String getSemTag() {
		return " (observable entity)";
	}

	public static void initialise(ContentPipelineManager cpm, 
								  Map<String, Map<String, LoincDetail>> loincDetailMap) throws TermServerScriptException {
		TemplatedConcept.cpm = cpm;
		TemplatedConcept.gl = cpm.getGraphLoader();

		termTweakingMap.put("702873001", "calculation"); // 702873001 |Calculation technique (qualifier value)|
		termTweakingMap.put("123029007", "point in time"); // 123029007 |Single point in time (qualifier value)|
		termTweakingMap.put("734842000", "source"); //734842000 |Source (property) (qualifier value)|
		termTweakingMap.put("718500008", "excretion"); //718500008 |Excretory process (qualifier value)|
		termTweakingMap.put("4421005", "cell"); //4421005 |Cell structure (cell structure)|
		
		//Populate removals into specific maps depending on how that removal will be processed.
		List<String> removals = Arrays.asList("submitted as specimen", SPECIMEN, "structure", "of", "at", "from");
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
		
		LoincTemplatedConcept.loincDetailMapAllTerms = loincDetailMap;
		
		percentAttribute = new RelationshipTemplate(gl.getConcept("246514001 |Units|"),
				gl.getConcept("415067009 |Percentage unit|"));
		conceptModelObjectAttrib = gl.getConcept("762705008 |Concept model object attribute|");
		precondition = gl.getConcept("704326004 |Precondition (attribute)|");
		relativeTo = gl.getConcept("704325000 |Relative to (attribute)|");
	}

	protected LoincTemplatedConcept(ExternalConcept externalConcept) {
		super(externalConcept);
		loincDetailMap = loincDetailMapAllTerms.get(externalConcept.getExternalIdentifier());
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

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		//Rules that apply to all templates:
		if (part.getPartNumber().equals("LP36683-8")) {
			//One off rule for ABO & Rh group
			addProcessingFlag(ProcessingFlag.SPLIT_TO_GROUP_PER_COMPONENT);
		}

		//If the value concept in LP map file is to a concept < 49062001 |Device (physical object)|
		// in which case “LOINC Method -> SNOMED 424226004 |Using device (attribute)|.
		if (gl.getDescendantsCache().getDescendants(DEVICE).contains(rt.getTarget())) {
			rt.setType(USING_DEVICE);
		}
	}

	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		if (!d.getType().equals(DescriptionType.FSN)) {
			String useAsAdditionalAcceptableTerm = d.getTerm();

			//LOINC will use their long common name as the PT
			d.setTerm(getLoincTerm().getLongCommonName());
			d.addIssue(CaseSensitivityUtils.FORCE_CS);

			//And we'll use the FSN minus the semantic tag as another acceptable term
			Description additionalAcceptableDesc = Description.withDefaults(useAsAdditionalAcceptableTerm, DescriptionType.SYNONYM, defaultAccAcceptabilityMap);
			getConcept().addDescription(additionalAcceptableDesc);
		}
	}

	protected static LoincTerm getLoincTerm(String loincNum) {
		return ((LoincScript)cpm).getLoincTerm(loincNum);
	}
	
	@Override
	protected void populateTerms() throws TermServerScriptException {

		super.populateTerms();
		
		//Add in the traditional colon form that we've previously used as the FSN
		String colonStr = getLoincTerm(getExternalIdentifier()).getColonizedTerm();
		Description colonDesc = Description.withDefaults(colonStr, DescriptionType.SYNONYM, defaultAccAcceptabilityMap);
		colonDesc.addIssue(CaseSensitivityUtils.FORCE_CS);
		concept.addDescription(colonDesc);
	}

	@Override
	protected String populateTemplateItem(String templateItem, String ptTemplateStr) throws TermServerScriptException {
		String regex = "\\[" + templateItem + "\\]";
		if (templateItem.equals(LOINC_PART_TYPE_METHOD)
				&& hasProcessingFlag(ProcessingFlag.SUPPRESS_METHOD_TERM)) {
			ptTemplateStr = ptTemplateStr.replaceAll(regex, "")
					.replace(" by ", "");
		} else if (templateItem.equals(LOINC_PART_TYPE_DIVISORS)
				&& hasProcessingFlag(ProcessingFlag.SUPPRESS_DIVISOR_TERM)) {
			ptTemplateStr = ptTemplateStr.replaceAll(regex, "");
			//Patch up the to in "to [DIVISORS]" since we removed the slot
			ptTemplateStr = ptTemplateStr.replace(" to  in", " in");
		} else if (templateItem.equals(LOINC_PART_TYPE_COMPONENT)
				&& hasProcessingFlag(ProcessingFlag.ALLOW_BLANK_COMPONENT)) {
			//We're not expecting a component, so set a debug point if do have one
			if (!concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, typeMap.get(templateItem), ActiveState.ACTIVE).isEmpty()) {
				LOGGER.debug("Check here - wasn't expecting to have a component: {}", this);
			}

			//We might have specified a slot for the component anyway eg as per rule 9 for Prid Observations
			String replacement = slotTermMap.get(templateItem);
			if (StringUtils.isEmpty(replacement)) {
				ptTemplateStr = ptTemplateStr.replaceAll(regex, "")
						.replace(" in ", "");
			} else {
				ptTemplateStr = ptTemplateStr.replaceAll(regex, replacement);
			}
		} else {
			return super.populateTemplateItem(templateItem, ptTemplateStr);
		}
		return ptTemplateStr;
	}

	@Override
	protected String populateTermTemplate(String itemStr, String templateItem, String ptStr, String partTypeName) {
		//Do we need to append any values to this term
		if (slotTermAppendMap.containsKey(partTypeName)) {
			itemStr += " " + slotTermAppendMap.get(partTypeName);
		}

		//Need the template, the item and the attribute value here to perform this one, so it needs to be here.
		//Special rule h.vi if we have "hours" in the TIME term, then change "at" to "in"
		if (partTypeName.equals(LOINC_PART_TYPE_TIME) && itemStr.contains("hours")) {
			ptStr = ptStr.replace(" at [TIME]", " in [TIME]");
		}

		ptStr = ptStr.replaceAll(templateItem, itemStr);
		return ptStr;
	}

	@Override
	protected String applyTermTweaking(IRelationship r, String term) {
		Concept value = r.getTarget();
		
		//Firstly, do we have a flat out replacement for this value?
		if (termTweakingMap.containsKey(value.getId())) {
			term = termTweakingMap.get(value.getId());
		}
		
		//Are we making any removals based on semantic tag?
		String semTag = SnomedUtilsBase.deconstructFSN(value.getFsn())[1];
		if (valueSemTagTermRemovalMap.containsKey(semTag)) {
			for (String removal : valueSemTagTermRemovalMap.get(semTag)) {
				term = term.replaceAll(removal, "");
				term = term.replaceAll(StringUtils.capitalizeFirstLetter(removal), "");
			}
		}

		term = checkTypeTermRemovalMap(r, term);
		
		return term;
	}

	private String checkTypeTermRemovalMap(IRelationship r, String term) {
		//Are we making any removals based on the type?
		if (typeValueTermRemovalMap.containsKey(r.getType())) {
			//Add a space to ensure we do whole word removal
			term = " " + term + " ";
			for (String removal : typeValueTermRemovalMap.get(r.getType())) {
				//Rule 2a. We sometimes allow 'specimen' to be used, for certain loinc parts
				if ((removal.equals(SPECIMEN) || removal.equals("from")) && hasProcessingFlag(ProcessingFlag.ALLOW_SPECIMEN)) {
					continue;
				}

				//Rule 2.h.iii.2 We leave in 'technique' for automated techniques
				if (removal.equals("technique") && hasProcessingFlag(ProcessingFlag.ALLOW_TECHNIQUE)) {
					continue;
				}
				String removalWithSpaces = " " + removal + " ";
				term = term.replace(removalWithSpaces, " ");

				//Try again capitalised
				removalWithSpaces = " " + StringUtils.capitalizeFirstLetter(removal) + " ";
				term = term.replace(removalWithSpaces, " ");
			}
			term = term.trim();
		}
		return term;
	}

	protected void populateParts() throws TermServerScriptException {
		prepareConceptDefaultedForModule(SCTID_LOINC_EXTENSION_MODULE);
		Set<String> partTypeSeen = new HashSet<>();
		for (LoincDetail loincDetail : loincDetailMap.values()) {
			populatePart(loincDetail, partTypeSeen);
		}
		
		//Ensure attributes are unique (considering both type and value)
		checkAndRemoveDuplicateAttributes();
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
		} else if (loincDetail.getPartName().equals(NO_VALUE_SPECIFIED)) {
			//Deliberately not setting the value is expected if this is a grouper
			if (!getExternalConcept().isGrouperConcept()) {
				LOGGER.warn("LoincNum {} has a part with no value specified, but this is not a grouper concept.  This is unexpected and may cause issues.", getExternalIdentifier());
				addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			} else {
				slotTermMap.put(LOINC_PART_TYPE_PROPERTY, "Measurement");
			}
		} else {
			boolean attributeAdded = addAttributeFromDetail(attributesToAdd, loincDetail);
			//Now if we didn't find a map, then for non-critical parts, we'll use the loinc part name anyway
			if (!attributeAdded && useTypesInPrimitive.contains(loincDetail.getPartTypeName())) {
				if (loincDetail.getPartNumber().equals(LoincScript.LOINC_TIME_PART)) {
					expectNullMap = true;
				} else {
					//We don't have a map here but we're going to allow the LOINC term to be used.
					//Special case for XXX which we'll override as specimen.  If you get any more
					//of these one-off rules, create a map of part name overrides
					if (loincDetail.getPartNumber().equals("LP7735-6")) {
						slotTermMap.put(loincDetail.getPartTypeName(), SPECIMEN);
					} else {
						slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
					}
					addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
					//If this is a grouper, then we can't have any primitives, thank you.
					if (getExternalConcept().isGrouperConcept()) {
						cpm.report( cpm.getTab(TAB_MODELING_ISSUES),
								getExternalIdentifier(),
								getExternalConcept().getLongDisplayName(),
								"Grouper concept with no mapping for " + loincDetail.getPartTypeName() + " excluded rather than marked primitive",
								loincDetail);
						addProcessingFlag(ProcessingFlag.DROP_OUT);
					}
				}
			}
		}

		//"Unknown" type parts should use the loinc description rather than the concept
		if (columnsToCheckForUnknownIndicators.contains(loincDetail.getLDTColumnName())
				&& containsUnknownPhrase(loincDetail.getPartName())) {
			//We don't want primitive groupers.  Drop out and report
			if (getExternalConcept().isGrouperConcept()) {
				int tab = cpm.getTab(TAB_MODELING_ISSUES);
				cpm.report(tab, getExternalIdentifier(),
					getExternalConcept().getLongDisplayName(),
					"Grouper part features some unknown word",
					loincDetail);
				addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			} else {
				slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
				addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			}
		}

		partTypeSeen.add(partTypeName);

		for (RelationshipTemplate rt : attributesToAdd) {
			addAttributesToConcept(rt, loincDetail, expectNullMap);
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

	//Relative and Ratio both use this default implementation.  The other templates override
	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttrib = typeMap.get(LOINC_PART_TYPE_COMPONENT);
		Concept challengeAttrib = typeMap.get(LOINC_PART_TYPE_CHALLENGE);
		if (hasDetailForColName(COMPONENT_PN) && hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttrib);
		} else {
			determineComponentAttributesWithSubParts(attributes, componentAttrib, challengeAttrib);
		}

		ensureComponentMappedOrRepresentedInTerm(attributes);
		return attributes;
	}

	private void determineComponentAttributesWithSubParts(List<RelationshipTemplate> attributes, Concept componentAttrib, Concept challengeAttrib) throws TermServerScriptException {
		LoincDetail denom = getLoincDetailForColNameIfPresent(COMPDENOM_PN);
		if (denom != null) {
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttrib);
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPDENOM_PN), relativeTo);

			//Check for percentage, unless this is a ratio
			if (denom.getPartName().contains("100") && !(this instanceof LoincTemplatedConceptWithRatio)) {
				attributes.add(percentAttribute);
				slotTermMap.put("PROPERTY", "percentage");
			}
		}

		if (detailPresent(COMPSUBPART2_PN)) {
			if(attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttrib);
			}
			if (!addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPSUBPART2_PN), challengeAttrib)) {
				//Did we not find a map for the challenge?  Then we're going to mark this as primitive
				addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			}
		}
	}

	public LoincTerm getLoincTerm() {
		return (LoincTerm) getExternalConcept();
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
	
	protected boolean hasDetailForPartType(String typeName) {
		String primaryColumnName = mapTypeToPrimaryColumn.get(typeName);
		return loincDetailMap.containsKey(primaryColumnName);
	}

	protected LoincDetail getLoincDetailForPartType(String typeName) throws TermServerScriptException {
		String primaryColumnName = mapTypeToPrimaryColumn.get(typeName);
		return getLoincDetailOrThrow(primaryColumnName);
	}

	protected boolean hasDetailForColName(String ldtColumnName) {
		//Does this LoincNum feature this particular detail?
		return loincDetailMap.containsKey(ldtColumnName);
	}

	protected LoincDetail getLoincDetailForColNameIfPresent(String ldtColumnName) {
		//What LoincDetails do we have for this loincNum?
		return loincDetailMap.get(ldtColumnName);
	}

	protected LoincDetail getLoincDetailOrThrow(String ldtColumnName) throws TermServerScriptException {
		LoincDetail loincDetail = getLoincDetailForColNameIfPresent(ldtColumnName);
		if (loincDetail == null) {
			throw new TermServerScriptException("Could not find detail with ldtColumnName " + ldtColumnName + " for loincNum " + getExternalIdentifier());
		}
		return loincDetail;
	}

	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return (detailPresent(COMPSUBPART3_PN) || detailPresent(COMPSUBPART4_PN));
	}
	
	protected boolean detailPresent(String ldtColumnName) {
		return getLoincDetailForColNameIfPresent(ldtColumnName) != null;
	}

	@Override
	protected boolean addAttributeFromDetailWithType(List<RelationshipTemplate> attributes, Part part, Concept attributeType) throws TermServerScriptException {
		LoincDetail loincDetail = (LoincDetail) part;
		try {
			if ((loincDetail.getPartTypeName().contentEquals("SYSTEM") && allowSpecimenTermForLoincParts.contains(loincDetail.getPartNumber()))
				|| (loincDetail.getLDTColumnName().equals(COMPNUM_PN) && loincDetail.getPartNumber().equals(LOINC_OBSERVATION_PART))) {
				addProcessingFlag(ProcessingFlag.ALLOW_SPECIMEN);
			}

			List<RelationshipTemplate> additionalAttributes = getAdditionalAttributes(loincDetail, attributeType);

			attributes.addAll(additionalAttributes);

			if (containsValue(AUTOMATED_TECHNIQUE, attributes)) {
				addProcessingFlag(ProcessingFlag.ALLOW_TECHNIQUE);
			}

			for (RelationshipTemplate rt : additionalAttributes) {
				applyTemplateSpecificModellingRules(attributes, part, rt);
			}
			return !additionalAttributes.isEmpty();
		} catch (TermServerScriptException e) {
			//If we've not found the COMPNUM_PN then we're not going to go ahead with this Loinc Term
			if (loincDetail.getLDTColumnName().equals("COMPNUM_PN")) {
				concept.addIssue(e.getMessage());
				addProcessingFlag(ProcessingFlag.DROP_OUT);
			} else {
				concept.addIssue(e.getMessage() + " definition status set to Primitive");
				this.concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			}
		}
		return false;
	}

	private List<RelationshipTemplate> getAdditionalAttributes(LoincDetail loincDetail, Concept attributeType) throws TermServerScriptException {
		String loincNum = getExternalIdentifier();
		String loincPartNum = loincDetail.getPartNumber();
		List<RelationshipTemplate> additionalAttributes = cpm.getAttributePartManager().getPartMappedAttributeForType(cpm.getTab(TAB_MODELING_ISSUES), loincNum, loincPartNum, attributeType);

		if (additionalAttributes.isEmpty()) {
			if (loincDetail.getPartNumber().equals(LoincScript.LOINC_TIME_PART)) {
				//Rule xi if we have a time, then we don't need to populate that field
				slotTermMap.put(loincDetail.getPartTypeName(), "");
			} else if (loincDetail.getPartNumber().equals(LOINC_OBSERVATION_PART)) {
				//Rule 2d We're going to allow the COMPONENT to be blank
				addProcessingFlag(ProcessingFlag.ALLOW_BLANK_COMPONENT);
				slotTermMap.put(loincDetail.getPartTypeName(), "");
			} else if (!skipSlotTermMapPopulation.contains(loincDetail.getPartTypeName())) {
				//Rule 2.c  If we don't have a part mapping, use what we do get in the FSN
				slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
			} else {
				throw new TermServerScriptException("Unable to find appropriate attribute mapping for " + loincNum + " / " + loincPartNum + " (" + loincDetail.getLDTColumnName() + ") - " + loincDetail.getPartName());
			}
		}
		return additionalAttributes;
	}

	private boolean containsValue(Concept c, List<RelationshipTemplate> attributes) {
		return attributes.stream()
				.anyMatch(rt -> rt.getTarget().equals(c));
	}

	public String toString() {
		return this.getClass().getSimpleName() + " for loincNum " + getExternalIdentifier();
	}

	protected void processSubComponents(List<RelationshipTemplate> attributes, Concept componentAttribType) throws TermServerScriptException {
		if (detailPresent(COMPSUBPART2_PN)) {
			if(attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
			}
			if (!addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPSUBPART2_PN), precondition)) {
				addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			}
		}

		addSubPartDetailIfPresent(COMPSUBPART3_PN, attributes, componentAttribType);
		addSubPartDetailIfPresent(COMPSUBPART4_PN, attributes, componentAttribType);
	}

	private void addSubPartDetailIfPresent(String subPartName, List<RelationshipTemplate> attributes, Concept componentAttribType) throws TermServerScriptException {
		if (detailPresent(subPartName)) {
			if (attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
			}
			LoincDetail componentDetail = getLoincDetailOrThrow(subPartName);
			slotTermAppendMap.put(LOINC_PART_TYPE_COMPONENT, componentDetail.getPartName());
			addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
		}
	}

	protected void ensureComponentMappedOrRepresentedInTerm(List<RelationshipTemplate> attributes) {
		//If we didn't find the component, return a null so that we record that failed mapping usage
		//And in fact, don't map this term at all
		if (attributes.isEmpty()) {
			attributes.add(null);
			if (!hasProcessingFlag(ProcessingFlag.ALLOW_BLANK_COMPONENT)
				&& !slotTermMap.containsKey(LOINC_PART_TYPE_COMPONENT)) {
				addProcessingFlag(ProcessingFlag.DROP_OUT);
			}
		}
	}

}
