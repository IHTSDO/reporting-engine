package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.*;
import java.util.Map.Entry;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LoincTemplatedConcept extends TemplatedConcept implements  LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConcept.class);
	private static final int GROUP_1 = 1;
	private static final String semTag = " (observable entity)";

	private static Set<String> skipSlotTermMapPopulation = new HashSet<>(Arrays.asList("PROPERTY", "COMPONENT", "DIVISORS"));
	
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
	protected static Set<String> unknownIndicators = new HashSet<>(Arrays.asList("unidentified", "other", "NOS", "unk sub", "unknown", "unspecified"));
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
		LoincTemplatedConcept.cpm = cpm;
		LoincTemplatedConcept.gl = gl;
		LoincTemplatedConcept.attributePartMapManager = attributePartMapManager;
		LoincTemplatedConcept.loincNumToLoincTermMap = loincNumToLoincTermMap;
		LoincTemplatedConcept.loincParts = loincParts;
		termTweakingMap.put("702873001", "calculation"); // 702873001 |Calculation technique (qualifier value)|
		termTweakingMap.put("123029007", "point in time"); // 123029007 |Single point in time (qualifier value)|
		
		//Populate removals into specific maps depending on how that removal will be processed.
		List<String> removals = Arrays.asList("submitted as specimen", "specimen", "structure", "of", "at", "from");
		typeValueTermRemovalMap.put(DIRECT_SITE, removals);
		
		removals = Arrays.asList("technique");
		typeValueTermRemovalMap.put(TECHNIQUE, removals);
		
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
				gl.getConcept(" 415067009 |Percentage unit|"));
		conceptModelObjectAttrib = gl.getConcept("762705008 |Concept model object attribute|");
		precondition = gl.getConcept("704326004 |Precondition (attribute)|");
		relativeTo = gl.getConcept("704325000 |Relative to (attribute)|");
	}

	protected LoincTemplatedConcept (String loincNum) {
		this.externalIdentifier = loincNum;
	}

	protected RelationshipTemplate applyTemplateSpecificRules(String loincPartNum, RelationshipTemplate rt) throws TermServerScriptException {
		//Rules that apply to all templates:

		//If the value concept in LP map file is to a concept < 49062001 |Device (physical object)|
		// in which case “LOINC Method -> SNOMED 424226004 |Using device (attribute)|.
		if (gl.getDescendantsCache().getDescendants(DEVICE).contains(rt.getTarget())) {
			rt.setType(USING_SUBST);
		}
		return rt;
	}
	
	protected int getTab(String tabName) throws TermServerScriptException {
		return cpm.getTab(tabName);
	}
	
	private static LoincTemplatedConcept getAppropriateTemplate(String loincNum, Map<String, LoincDetail> loincDetailMap) throws TermServerScriptException {
		LoincDetail loincDetail = getPartDetail(loincNum, loincDetailMap, "PROPERTY");
		switch (loincDetail.getPartName()) {
			/*case "Ratio" :
			case "MRto" :
			case "VFr" :
			case "NFr" : return LoincTemplatedConceptWithRelative.create(loincNum);*/
			case "ACnc" :
			case "Angle" :
			case "CCnc" :
			case "CCnt" :
			case "Diam" :
			case "LaCnc" :
			case "LnCnc" :
			case "LsCnc" :
			case "MCnc" :
			case "MCnt" :
			case "MoM" :
			case "NCnc" :
			case "Naric" :
			case "PPres" :
			case "PrThr" :
			case "SCnc" :
			case "SCnt" :
			case "Titr" :
			case "Visc" : return LoincTemplatedConceptWithComponent.create(loincNum);
			//case "EntMass" :
			case "Anat" :
			case "Aper" :
			case "EntVol" :
			case "ID" :
			case "Morph" :
			case "Prid" :
			case "Temp" :
			case "Type" :
			case "Vol" : return LoincTemplatedConceptWithInheres.create(loincNum);
		}
		return null;
	}

	public static LoincTemplatedConcept populateTemplate(String loincNum, Map<String, LoincDetail> details) throws TermServerScriptException {
		
		/*if (loincNum.equals("50407-6")) {
			LOGGER.debug("Check for missing component - should result in loinc term being dropped entirely");
		}*/
		
		LoincTemplatedConcept templatedConcept = getAppropriateTemplate(loincNum, details);
		if (templatedConcept != null) {
			templatedConcept.populateParts(details);
			templatedConcept.populateTerms(loincNum, details);
			if (detailsIndicatePrimitiveConcept(loincNum) || 
					templatedConcept.hasProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE)) {
				templatedConcept.getConcept().setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			}
			templatedConcept.getConcept().addAlternateIdentifier(loincNum, SCTID_LOINC_CODE_SYSTEM);
		}
		return templatedConcept;
	}

	public boolean hasProcessingFlag(ProcessingFlag flag) {
		return processingFlags.contains(flag);
	}

	private void populateTerms(String loincNum, Map<String, LoincDetail> details) throws TermServerScriptException {
		//Start with the template PT and swap out as many parts as we come across
		String ptTemplateStr = preferredTermTemplate;
		
		/*Set<String> partTypeSeen = new HashSet<>();
		for (LoincDetail detail : details.values()) {
			//We have multiple details for the component, so no need to try populate multiple times
			if (!partTypeSeen.contains(detail.getPartTypeName())) {
				ptTemplateStr = populateTermTemplateFromAttribute(ptTemplateStr, detail);
				partTypeSeen.add(detail.getPartTypeName());
			}
		}*/
		//TODO Don't really need to do both of these things - consolidate
		ptTemplateStr = populateTermTemplateFromSlots(ptTemplateStr);
		ptTemplateStr = tidyUpTerm(loincNum, ptTemplateStr);
		ptTemplateStr = StringUtils.capitalizeFirstLetter(ptTemplateStr);
		Description pt = Description.withDefaults(ptTemplateStr, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		Description fsn = Description.withDefaults(ptTemplateStr + semTag, DescriptionType.FSN, Acceptability.PREFERRED);
		
		//Also add the Long Common Name as a Synonym
		String lcnStr = loincNumToLoincTermMap.get(loincNum).getLongCommonName();
		Description lcn = Description.withDefaults(lcnStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		//Override the case significance for these
		lcn.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		
		//Add in the traditional colon form that we've previously used as the FSN
		String colonStr = loincNumToLoincTermMap.get(loincNum).getColonizedTerm();
		Description colonDesc = Description.withDefaults(colonStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		colonDesc.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);

		concept.addDescription(pt);
		concept.addDescription(fsn);
		concept.addDescription(lcn);
		concept.addDescription(colonDesc);
	}

	/*private String populateTermTemplateFromAttribute(String ptTemplateStr, LoincDetail detail) throws TermServerScriptException {
		if (skipPartTypes.contains(detail.getPartTypeName())) {
			return ptTemplateStr;
		}
		String templateItem = "\\[" + detail.getPartTypeName() + "\\]";
		RelationshipTemplate rt = null;
		
		//Do we have this slot name defined for us?
		if (slotTermMap.containsKey(detail.getPartTypeName())) {
			//ptTemplateStr = populateTermTemplate(slotTermMap.get(loincPart.getPartTypeName()), templateItem, ptTemplateStr);
			String itemStr = StringUtils.decapitalizeFirstLetter(slotTermMap.get(detail.getPartTypeName()));
			ptTemplateStr = ptTemplateStr.replaceAll(templateItem, itemStr);
		} else {
			Concept attributeType = typeMap.get(detail.getPartTypeName());
			if (attributeType == null) {
				TermServerScript.info("Failed to find attribute type for " + loincNum + ": " + detail.getPartTypeName() + " in template " + this.getClass().getSimpleName());
			} else {
				try {
					Set<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
					if (rels.size() == 1) {
						Relationship r = rels.iterator().next();
						rt = new RelationshipTemplate(r);
					}
				} catch (Exception e) {
					TermServerScript.info("Failed to find attribute via type for " + loincNum + ": " + detail.getPartTypeName() + " in template " + this.getClass().getSimpleName() + " due to " + e.getMessage());
				}
			}
			
			if (rt != null) {
				ptTemplateStr = populateTermTemplate(rt, templateItem, ptTemplateStr, detail.getPartTypeName());
			} else {
				//TermServerScript.debug("No attribute during FSN gen for " + detail);
			}
		}
		return ptTemplateStr;
	}*/

	private String populateTermTemplateFromSlots(String ptTemplateStr) throws TermServerScriptException {
		//Do we have any slots left to fill?  Find their attribute types via the slot map
		String [] templateItems = org.apache.commons.lang3.StringUtils.substringsBetween(ptTemplateStr,"[", "]");
		if (templateItems == null) {
			return ptTemplateStr;
		}
		
		for (String templateItem : templateItems) {
			String regex = "\\[" + templateItem + "\\]";
			if (slotTermMap.containsKey(templateItem)) {
				String itemStr = StringUtils.decapitalizeFirstLetter(slotTermMap.get(templateItem));
				ptTemplateStr = ptTemplateStr.replaceAll(regex, itemStr);
				//Did we just wipe out a value?  Trim any trailing connecting words like 'at [TIME]' if so
				if (StringUtils.isEmpty(itemStr)) {
					//TODO This will probably become a list
					if (ptTemplateStr.contains(" at ")) {
						ptTemplateStr = ptTemplateStr.replace(" at ", "");
					}
				}
			} else {
				Concept attributeType = typeMap.get(templateItem);
				if (attributeType == null) {
					concept.addIssue("Token " + templateItem + " missing from typeMap in " + this.getClass().getSimpleName());
					return ptTemplateStr;
				}
				Set<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
				if (rels.size() == 0) {
					continue;
				}
				if (rels.size() > 1) {
					throw new TermServerScriptException(rels.size() + " relationships for " + attributeType + " in " + getExternalIdentifier());
				}
				RelationshipTemplate rt = new RelationshipTemplate(rels.iterator().next());
				ptTemplateStr = populateTermTemplate(rt, regex, ptTemplateStr, templateItem);
			}
		}
		return ptTemplateStr;
	}


	private String populateTermTemplate(RelationshipTemplate rt, String templateItem, String ptStr, String partTypeName) throws TermServerScriptException {
		//TODO Detect GB Spelling and break out another term
		Description targetPt = rt.getTarget().getPreferredSynonym(US_ENG_LANG_REFSET);
		String itemStr = targetPt.getTerm();
		
		itemStr = applyTermTweaking(rt, itemStr);
		
		//Do we need to append any values to this term
		if (slotTermAppendMap.containsKey(partTypeName)) {
			itemStr += " " + slotTermAppendMap.get(partTypeName);
		}
		//Can we make this lower case?
		if (targetPt.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) || 
				targetPt.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
			itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
		}
		ptStr = ptStr.replaceAll(templateItem, itemStr);
		return ptStr;
	}
	
	/*private String populateTermTemplate(String itemStr, String templateItem, String ptStr) throws TermServerScriptException {
		itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
		ptStr = ptStr.replaceAll(templateItem, itemStr);
		return ptStr;
	}*/

	private String applyTermTweaking(RelationshipTemplate rt, String term) {
		Concept value = rt.getTarget();
		
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
		if (typeValueTermRemovalMap.containsKey(rt.getType())) {
			//Add a space to ensure we do whole word removal
			term = " " + term + " ";
			for (String removal : typeValueTermRemovalMap.get(rt.getType())) {
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

	protected String tidyUpTerm(String loincNum, String term) {
		term = replaceAndWarn(loincNum, term, " at [TIME]");
		term = replaceAndWarn(loincNum, term, " by [METHOD]");
		term = replaceAndWarn(loincNum, term, " in [SYSTEM]");
		term = replaceAndWarn(loincNum, term, " using [DEVICE]");
		term = replaceAndWarn(loincNum, term, " [CHALLENGE]");
		term = term.replaceAll("  ", " ");
		return term;
	}

	private String replaceAndWarn(String loincNum, String term, String str) {
		if (term.contains(str)) {
			//TermServerScript.warn(loincNum + " did not provide '" + str + "'");
			//Need to make string regex safe
			str = str.replaceAll("\\[","\\\\\\[").replaceAll("\\]","\\\\\\]");
			term = term.replaceAll(str, "");
		}
		return term;
	}

	private void populateParts(Map<String, LoincDetail> details) throws TermServerScriptException {
		concept = Concept.withDefaults(Integer.toString((++conceptsModelled)));
		concept.setModuleId(SCTID_LOINC_EXTENSION_MODULE);
		concept.addRelationship(IS_A, OBSERVABLE_ENTITY);
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
		Set<String> partTypeSeen = new HashSet<>();
		for (LoincDetail loincDetail : details.values()) {
			String partTypeName = loincDetail.getPartTypeName();
			if (skipPartTypes.contains(partTypeName)) {
				continue;
			}
			if (partTypeSeen.contains(partTypeName)) {
				continue;
			}
			if (skipLDTColumnNames.contains(loincDetail.getLDTColumnName())) {
				continue;
			}

			boolean expectNullMap = false;
			boolean isComponent = partTypeName.equals("COMPONENT");
			List<RelationshipTemplate> attributesToAdd = new ArrayList<>();
			if (isComponent) {
				//We're only going to process the COMPNUM as that the mapping we're really interested in.
				if (!loincDetail.getLDTColumnName().equals(LoincDetail.COMPNUM_PN)) {
					continue;
				}
				ArrayList<String> issues = new ArrayList<>();
				attributesToAdd = determineComponentAttributes(externalIdentifier, issues);
				concept.addIssues(issues, ",\n");
			} else {
				RelationshipTemplate rt = getAttributeForLoincPart(getTab(TAB_MODELING_ISSUES), loincDetail);
				attributesToAdd = Collections.singletonList(rt);
				//Now if we didn't find a map, then for non-critical parts, we'll used the loinc part name anyway
				if (rt == null && useTypesInPrimitive.contains(loincDetail.getPartTypeName())) {
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
				if (rt != null) {
					mapped++;
					//concept.addRelationship(rt, SnomedUtils.getFirstFreeGroup(concept));
					//New Observables model.  Everything grouped
					concept.addRelationship(rt, GROUP_1);
				} else if (!expectNullMap){
					unmapped++;
					String issue = "Not Mapped - " + loincDetail.getPartTypeName() + " | " + loincDetail.getPartNumber() + "| " + loincDetail.getPartName();
					cpm.report(getTab(TAB_MODELING_ISSUES),
							externalIdentifier,
						loincDetail.getPartNumber(),
						issue,
						loincDetail.getPartName());
					concept.addIssue(issue, ",\n");
					concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
					partNumsUnmapped.add(loincDetail.getPartNumber());
					
					//Record the fact that we failed to find a map on a per property basis
					//addFailedMapping(loincNum, loincDetail.getPartNumber());
					
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
		}
		
		//Ensure attributes are unique (duplicates caused by multiple information sources)
		checkAndRemoveDuplicateAttributes();
		
		if (concept.hasIssues()) {
			concept.addIssue("Template used: " + this.getClass().getSimpleName(), ",\n");
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
			TermServerScript.warn("Removed a redundant " + r + " from " + this);
		}
	}

	protected abstract List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException;

	private RelationshipTemplate getAttributeForLoincPart(int idxTab, LoincDetail loincDetail) throws TermServerScriptException {
		//Now given the template that we've chosen for this LOINC Term, what attribute
		//type would we use?
		Concept attributeType = typeMap.get(loincDetail.getPartTypeName());
		if (attributeType == null) {
			if (idxTab != NOT_SET) {
				cpm.report(idxTab,
						externalIdentifier,
					loincDetail.getPartNumber(),
					"Type in context not identified - " + loincDetail.getPartTypeName() + " | " + this.getClass().getSimpleName(),
					loincDetail.getPartName());
			}
			return null;
		}
		
		if (loincDetail.getPartTypeName().contentEquals("SYSTEM") && allowSpecimenTermForLoincParts.contains(loincDetail.getPartNumber())) {
			cpm.report(getTab(TAB_IOI), "Allow use of 'specimen'", externalIdentifier);
			processingFlags.add(ProcessingFlag.ALLOW_SPECIMEN);
		}
		RelationshipTemplate rt = attributePartMapManager.getPartMappedAttributeForType(idxTab, externalIdentifier, loincDetail.getPartNumber(), attributeType);

		if (rt == null) {
			if (loincDetail.getPartNumber().equals(LoincScript.LOINC_TIME_PART)) {
				//Rule xi if we have a time, then we don't need to populate that field
				slotTermMap.put(loincDetail.getPartTypeName(), "");
			} else if (!skipSlotTermMapPopulation.contains(loincDetail.getPartTypeName())) {
				//Rule 2.c  If we don't have a part mapping, use what we do get in the FSN
				slotTermMap.put(loincDetail.getPartTypeName(), loincDetail.getPartName());
			}
		}
		return rt;
	}

	private static LoincDetail getPartDetail(String loincNum, Map<String, LoincDetail> loincDetailMap, String partTypeName) throws TermServerScriptException {
		for (LoincDetail detail : loincDetailMap.values()) {
			if (detail.getPartTypeName().equals(partTypeName)) {
				return detail;
			}
		}
		throw new TermServerScriptException("Unable to find part " + partTypeName + " in " + loincNum);
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
	protected boolean CompNumPnIsSafe(String loincNum) throws TermServerScriptException {
		LoincDetail ldComponentPn = getLoincDetail(loincNum, LoincDetail.COMPONENT_PN);
		LoincDetail ldCompNum = getLoincDetail(loincNum, LoincDetail.COMPNUM_PN);
		
		if (ldComponentPn == null || ldCompNum == null) {
			throw new TermServerScriptException(loincNum + " detail did not feature COMPONENT_PN or COMPNUM_PN");
		}
		
		return ldComponentPn.getPartNumber().equals(ldCompNum.getPartNumber()) &&
				ldComponentPn.getPartName().equals(ldCompNum.getPartName());
	}

	protected static LoincDetail getLoincDetailIfPresent(String loincNum, String ldtColumnName) throws TermServerScriptException {
		//What LoincDetails do we have for this loincNum?
		Map<String, LoincDetail> partDetailMap = loincDetailMap.get(loincNum);
		if (partDetailMap == null) {
			throw new TermServerScriptException("No LOINC part details found for loincNum: " + loincNum);
		}
		return partDetailMap.get(ldtColumnName);
	}
	
	protected static LoincDetail getLoincDetail(String loincNum, String ldtColumnName) throws TermServerScriptException {
		LoincDetail loincDetail = getLoincDetailIfPresent(loincNum, ldtColumnName);
		if (loincDetail == null) {
			throw new TermServerScriptException("Could not find detail with ldtColumnName " + ldtColumnName + " for loincNum " + loincNum);
		}
		return loincDetail;
	}
	
	protected static boolean detailsIndicatePrimitiveConcept(String loincNum) throws TermServerScriptException {
		if (detailPresent(loincNum, LoincDetail.COMPSUBPART3_PN) ||
			detailPresent(loincNum, LoincDetail.COMPSUBPART4_PN)) {
			return true;
		}
		
		return false;
	}
	
	protected static boolean detailPresent(String loincNum, String ldtColumnName) throws TermServerScriptException {
		return getLoincDetailIfPresent(loincNum, ldtColumnName) != null;
	}
	
	protected RelationshipTemplate getAttributeFromDetailWithType(String loincNum, String ldtColumnName, Concept attributeType) throws TermServerScriptException {
		LoincDetail loincDetail = getLoincDetail(loincNum, ldtColumnName);
		String loincPartNum = loincDetail.getPartNumber();

		//LOINC Time has a special allowance to fail to map without causing the concept to become primitive
		if (loincPartNum.equals(LoincScript.LOINC_TIME_PART)) {
			return null;
		}
		if (!attributePartMapManager.containsMappingForLoincPartNum(loincPartNum)) {
			throw new TermServerScriptException("Unable to find any attribute mapping for " + loincNum + " / " + loincPartNum + " (" + ldtColumnName + ")" );
		}
		RelationshipTemplate rt = attributePartMapManager.getPartMappedAttributeForType(getTab(TAB_MODELING_ISSUES), loincNum, loincPartNum, attributeType);

		if (rt == null) {
			throw new TermServerScriptException("Unable to find appropriate attribute mapping for " + loincNum + " / " + loincPartNum + " (" + ldtColumnName + ")" );
		}

		rt = applyTemplateSpecificRules(loincPartNum, rt);
		rt.setType(attributeType);
		return rt;
	}

	protected boolean addAttributeFromDetailWithType(List<RelationshipTemplate> attributes, String loincNum, String ldtColumnName,
			List<String> issues, Concept type) {
		boolean attributeAdded = false;
		try {
			attributes.add(getAttributeFromDetailWithType(loincNum, ldtColumnName, type));
			attributeAdded = true;
		} catch (TermServerScriptException e) {
			//If we've not found the COMPNUM_PN then we're not going to go ahead with this Loinc Term
			if (ldtColumnName.equals("COMPNUM_PN")) {
				issues.add(e.getMessage());
				processingFlags.add(ProcessingFlag.DROP_OUT);
			} else {
				//TODO Stop passing issues around, we have access to the concept here
				issues.add(e.getMessage() + " definition status set to Primitive");
				this.concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			}
		}
		return attributeAdded;
	}
	
	/*protected void addFailedMapping(String loincNum, String partNum) throws TermServerScriptException {
		//What is the property for this loincNum?
		LoincDetail propertyDetail = loincDetailMap.get(loincNum).get(LoincDetail.PROPERTY);
		String property = propertyDetail.getPartName();
		
		/*Have we seen this partNum for any _other_ properties before?
		for (String thisProperty : failedMappingsByProperty.keySet()) {
			if (thisProperty.equals(property)) {
				continue;
			} else if (failedMappingsByProperty.get(thisProperty).contains(partNum)) {
				TermServerScript.warn(partNum + " seen for multiple properties: " + property + " + " + thisProperty);
				failedMappingAlreadySeenForOtherProperty++;
				return;
			}
		}*/
		
		/*Have we seen any partNums for this property before
		Set<String> partNums = failedMappingsByProperty.get(property);
		if (partNums == null) {
			partNums = new HashSet<>();
			failedMappingsByProperty.put(property, partNums);
		}
		partNums.add(partNum);
	}*/
	
	/*public static void reportFailedMappingsByProperty(int tabIdx) throws TermServerScriptException {
		ts.report(tabIdx, "");
		ts.report(tabIdx, "Failed Mapping Summary Counts per Property");
		for (String property : failedMappingsByProperty.keySet()) {
			ts.report(tabIdx, property, failedMappingsByProperty.get(property).size());
		}
		ts.report(tabIdx, "Failed Mapping Already Seen For Other Property", failedMappingAlreadySeenForOtherProperty);
	}*/

	public static void reportMissingMappings(int tabIdx) {
		//This list needs to be sorted based on a rank + usage metric
		unmappedPartUsageMap.entrySet().stream()
			.sorted((k1, k2) -> k1.getValue().compareTo(k2.getValue()))
			.forEach(e -> reportMissingMap(tabIdx, e));
		
	}

	private static void reportMissingMap(int tabIdx, Entry<String, LoincUsage> entry) {
		try {
			String loincPartNum = entry.getKey();
			if (!loincParts.containsKey(loincPartNum)) {
				cpm.report(tabIdx, loincPartNum, "Unknown", "", "Part num not known to list of parts.  Check origin.");
			} else {
				String loincPartName = loincParts.get(loincPartNum).getPartName();
				String loincPartType = loincParts.get(loincPartNum).getPartTypeName();
				LoincUsage usage = entry.getValue();
				cpm.report(tabIdx, loincPartNum, loincPartName, loincPartType, usage.getPriority(), usage.getCount(), usage.getTopRankedLoincTermsStr());
			}
		} catch (TermServerScriptException e) {
			throw new RuntimeException(e);
		}
	}
	
	public String toString() {
		return this.getClass().getSimpleName() + " for loincNum " + externalIdentifier;
	}

}
