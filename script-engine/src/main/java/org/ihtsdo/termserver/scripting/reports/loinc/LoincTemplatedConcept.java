package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public abstract class LoincTemplatedConcept implements ScriptConstants, ConceptWrapper, LoincConstants {
	
	private static final String semTag = " (observable entity)";
	private static Set<String> partNumsMapped = new HashSet<>();
	private static Set<String> partNumsUnmapped = new HashSet<>();
	private static int mapped = 0;
	private static int unmapped = 0;
	private static int skipped = 0;
	private static int MAPPING_DETAIL_TAB = TERTIARY_REPORT;
	private static int TAB_MODELING_NOTES = QUINARY_REPORT;
	private static int conceptsModelled = 0;
	
	protected static TermServerScript ts;
	protected static GraphLoader gl;
	protected static Map<String, RelationshipTemplate> loincPartAttributeMap = new HashMap<>();
	protected static Map<String, LoincTerm> loincNumToLoincTermMap = new HashMap<>();
	protected static RelationshipTemplate percentAttribute;
	
	protected static Map<String, String> termTweakingMap = new HashMap<>();
	protected static Map<Concept, Set<String>> typeValueTermRemovalMap = new HashMap<>();
	protected static Map<String, Set<String>> valueSemTagTermRemovalMap = new HashMap<>();
	protected static Set<String> problematicParts;
	
	//Map of LoincNums to ldtColumnNames to details
	protected static Map<String, Map<String, LoincDetail>> loincDetailMap;
	
	protected String loincNum;
	protected Map<String, Concept> typeMap = new HashMap<>();
	protected String preferredTermTemplate;
	protected Concept concept;
	
	
	
	public static void initialise(TermServerScript ts, GraphLoader gl, 
			Map<String, RelationshipTemplate> loincPartAttributeMap,
			Map<String, LoincTerm> loincNumToLoincTermMap,
			Set<String> problematicParts, 
			Map<String, Map<String, LoincDetail>> loincDetailMap) throws TermServerScriptException {
		LoincTemplatedConcept.ts = ts;
		LoincTemplatedConcept.gl = gl;
		LoincTemplatedConcept.loincPartAttributeMap = loincPartAttributeMap;
		LoincTemplatedConcept.loincNumToLoincTermMap = loincNumToLoincTermMap;
		termTweakingMap.put("702873001", "calculation"); // 702873001 |Calculation technique (qualifier value)|
		termTweakingMap.put("123029007", "point in time"); // 123029007 |Single point in time (qualifier value)|
		
		Set<String> removals = new HashSet<>(Arrays.asList("specimen", "of", "at", "from"));
		typeValueTermRemovalMap.put(DIRECT_SITE, removals);
		
		removals = new HashSet<>(Arrays.asList("clade", "class", "division", "domain", "family", "genus", 
				"infraclass", "infraclass", "infrakingdom", "infraorder", "infraorder", "kingdom", "order", 
				"phylum", "species", "subclass", "subdivision", "subfamily", "subgenus", "subkingdom", 
				"suborder", "subphylum", "subspecies", "superclass", "superdivision", "superfamily", 
				"superkingdom", "superorder", "superphylum"));
		valueSemTagTermRemovalMap.put("(organism)", removals);
		LoincTemplatedConcept.problematicParts = problematicParts;
		LoincTemplatedConcept.loincDetailMap = loincDetailMap;
		
		percentAttribute = new RelationshipTemplate(gl.getConcept("246514001 |Units|"),
				gl.getConcept(" 415067009 |Percentage unit|"));
	}

	protected LoincTemplatedConcept (String loincNum) {
		this.loincNum = loincNum;
	}

	public static LoincTemplatedConcept populateModel(String loincNum, List<LoincPart> loincParts) throws TermServerScriptException {
		LoincTemplatedConcept templatedConcept = getAppropriateTemplate(loincNum, loincParts);
		if (templatedConcept != null) {
			templatedConcept.populateParts(loincParts);
			templatedConcept.populateTerms(loincNum, loincParts);
			if (detailsIndicatePrimitiveConcept(loincNum)) {
				templatedConcept.getConcept().setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			}
		}
		return templatedConcept;
	}

	private void populateTerms(String loincNum, List<LoincPart> loincParts) throws TermServerScriptException {
		//Start with the template PT and swap out as many parts as we come across
		String ptStr = preferredTermTemplate;
		for (LoincPart loincPart : loincParts) {
			if (loincPart.getPartTypeName().equals("CLASS") || 
					loincPart.getPartTypeName().equals("SUFFIX")) {
				continue;
			}
			String templateItem = "\\[" + loincPart.getPartTypeName() + "\\]";
			RelationshipTemplate rt = getAttributeForLoincPart(NOT_SET, loincPart);
			
			//If we don't find it from the loinc part, can we work it out from the attribute mapping directly?
			if (rt == null) {
				Concept attributeType = typeMap.get(loincPart.getPartTypeName());
				if (attributeType == null) {
					TermServerScript.info("Failed to find attribute type for " + loincNum + ": " + loincPart.getPartTypeName() + " in template " + this.getClass().getSimpleName());
				} else {
					try {
						Relationship r = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE).iterator().next();
						rt = new RelationshipTemplate(r);
					} catch (Exception e) {
						TermServerScript.info("Failed to find attribute via type for " + loincNum + ": " + loincPart.getPartTypeName() + " in template " + this.getClass().getSimpleName() + " due to " + e.getMessage());;
					}
				}
			}
			
			if (rt != null) {
				ptStr = populateTermTemplate(rt, templateItem, ptStr);
			} else {
				TermServerScript.debug("No attribute during FSN gen for " + loincNum + " / " + loincPart);
			}
		}
		ptStr = tidyUpTerm(loincNum, ptStr);
		ptStr = StringUtils.capitalizeFirstLetter(ptStr);
		Description pt = Description.withDefaults(ptStr, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		Description fsn = Description.withDefaults(ptStr + semTag, DescriptionType.FSN, Acceptability.PREFERRED);
		
		//Also add the Long Common Name as a Synonym
		String lcnStr = loincNumToLoincTermMap.get(loincNum).getLongCommonName();
		Description lcn = Description.withDefaults(lcnStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		//Override the case significance for these
		lcn.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		
		//Add in the traditional colon form that we've previously used as the FSN
		String colonStr = loincNumToLoincTermMap.get(loincNum).getColonizedTerm();
		Description colonDesc = Description.withDefaults(colonStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		colonDesc.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		
		//And the LoncNum itself, until we have the Identifier File available to use
		String lnStr = LoincUtils.buildLoincNumTerm(loincNum);
		Description ln = Description.withDefaults(lnStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		
		concept.addDescription(pt);
		concept.addDescription(fsn);
		concept.addDescription(lcn);
		concept.addDescription(ln);
		concept.addDescription(colonDesc);
	}

	private String populateTermTemplate(RelationshipTemplate rt, String templateItem, String ptStr) throws TermServerScriptException {
		//TODO Detect GB Spelling and break out another term
		Description targetPt = rt.getTarget().getPreferredSynonym(US_ENG_LANG_REFSET);
		String itemStr = targetPt.getTerm();
		
		itemStr = applyTermTweaking(rt, itemStr);
		//Can we make this lower case?
		if (targetPt.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) || 
				targetPt.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
			itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
		}
		ptStr = ptStr.replaceAll(templateItem, itemStr);
		return ptStr;
	}

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
				removal = " " + removal + " ";
				term = term.replaceAll(removal, " ");
				term = term.replaceAll(StringUtils.capitalizeFirstLetter(removal), " ");
			}
			term = term.trim();
		}
		
		return term;
	}

	protected String tidyUpTerm(String loincNum, String term) {
		term = replaceAndWarn(loincNum, term, " at [TIME]");
		term = replaceAndWarn(loincNum, term, " by [METHOD]");
		term = replaceAndWarn(loincNum, term, " to [DIVISOR]");
		term = replaceAndWarn(loincNum, term, " in [SYSTEM]");
		term = term.replaceAll("  ", " ");
		return term;
	}

	private String replaceAndWarn(String loincNum, String term, String str) {
		if (term.contains(str)) {
			TermServerScript.warn(loincNum + " did not provide '" + str + "'");
			//Need to make string regex safe
			str = str.replaceAll("\\[","\\\\\\[").replaceAll("\\]","\\\\\\]");
			term = term.replaceAll(str, "");
		}
		return term;
	}

	private void populateParts(List<LoincPart> loincParts) throws TermServerScriptException {
		concept = Concept.withDefaults(Integer.toString((++conceptsModelled)));
		concept.setModuleId(SCTID_LOINC_EXTENSION_MODULE);
		concept.addRelationship(IS_A, OBSERVABLE_ENTITY);
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
		Set<LoincPart> loincPartsSeen = new HashSet<>();
		for (LoincPart loincPart : loincParts) {
			//Have we seen this loincPart before?
			if (loincPartsSeen.contains(loincPart)) {
				TermServerScript.warn("Duplicate LOINC Part seen: " + loincPart);
				continue;
			}
			
			//Is this LOINC Part one of our problematic ones (more than one possible SNOMED concept mapped)?
			if (problematicParts.contains(loincPart.getPartNumber())) {
				ts.report(MAPPING_DETAIL_TAB,
						loincNum,
						loincPart.getPartNumber(),
						"Problematic Mapping",
						loincPart.getPartName(),
						"Multiple SNOMED concepts potentially map");
			}
			
			loincPartsSeen.add(loincPart);
			
			boolean isComponent = loincPart.getPartTypeName().equals("COMPONENT");
			List<RelationshipTemplate> attributesToAdd = new ArrayList<>();
			if (isComponent) {
				ArrayList<String> issues = new ArrayList<>();
				attributesToAdd = determineComponentAttributes(loincNum, issues);
				concept.addIssues(issues, ",\n");
			} else {
				attributesToAdd = Collections.singletonList(getAttributeForLoincPart(MAPPING_DETAIL_TAB, loincPart));
			}
			
			for (RelationshipTemplate rt : attributesToAdd) {
				if (rt != null) {
					mapped++;
					ts.report(MAPPING_DETAIL_TAB,
						loincNum,
						loincPart.getPartNumber(),
						"Mapped OK",
						loincPart.getPartName(),
						rt);
					partNumsMapped.add(loincPart.getPartNumber());
					concept.addRelationship(rt, SnomedUtils.getFirstFreeGroup(concept));
				} else {
					unmapped++;
					String issue = "Not Mapped - " + loincPart.getPartTypeName() + " | " + loincPart.getPartNumber() + "| " + loincPart.getPartName();
					ts.report(MAPPING_DETAIL_TAB,
						loincNum,
						loincPart.getPartNumber(),
						issue,
						loincPart.getPartName());
					concept.addIssue(issue, ",\n");
					partNumsUnmapped.add(loincPart.getPartNumber());
				}
			}
		}
		
		//Ensure attributes are unique (duplicates caused by multiple information sources)
		checkAndRemoveDuplicateAttributes();
		
		if (concept.hasIssues()) {
			concept.addIssue("Template used: " + this.getClass().getSimpleName(), ",\n");
		}
	}

	protected void checkAndRemoveDuplicateAttributes() {
		Set<RelationshipTemplate> relsSeen = new HashSet<>();
		Set<Relationship> relsToRemove = new HashSet<>();
		for (Relationship r : concept.getRelationships()) {
			RelationshipTemplate rt = new RelationshipTemplate(r);
			if (relsSeen.contains(rt)) {
				relsToRemove.add(r);
			}
		}
		
		for (Relationship r : relsToRemove) {
			concept.getRelationships().remove(r);
			TermServerScript.warn("Removed a redundant " + r + " from " + this);
		}
	}

	protected abstract List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException;

	private RelationshipTemplate getAttributeForLoincPart(int idxTab, LoincPart loincPart) throws TermServerScriptException {
		RelationshipTemplate rt = loincPartAttributeMap.get(loincPart.getPartNumber());
		if (loincPart.getPartTypeName().equals("CLASS")) {
			skipped++;
			return null;
		}
		
		//Now given the template that we've chosen for this LOINC Term, what attribute
		//type would we use?
		Concept attributeType = typeMap.get(loincPart.getPartTypeName());
		if (attributeType == null) {
			if (idxTab != NOT_SET) {
				ts.report(idxTab,
					loincNum,
					loincPart.getPartNumber(),
					"Type in context not identified - " + loincPart.getPartTypeName() + " | " + this.getClass().getSimpleName(),
					loincPart.getPartName());
			}
		} else if (rt != null) {
			rt.setType(attributeType);
		}
		return rt;
	}

	private static LoincTemplatedConcept getAppropriateTemplate(String loincNum, List<LoincPart> loincParts) throws TermServerScriptException {
		for (LoincPart loincPart : loincParts) {
			switch (loincPart.getPartName()) {
				case "Ratio" :
				case "MRto" :
				case "VFr" :
				case "NFr" : return LoincTemplatedConceptWithRelative.create(loincNum);
				case "PrThr" :
				case "PPres" :
				case "CCnc" :
				case "MCnc" :
				case "SCnc" :
				case "ACnc" :
				case "NCnc" :
				case "LsCnc" :
				case "Naric" :
				case "MoM" :
				case "EntMass" : return LoincTemplatedConceptWithComponent.create(loincNum);
				case "Prid" :
				case "EntVol" :
				case "Vol" :
				case "Type" : return LoincTemplatedConceptWithInheres.create(loincNum);
			}
		}
		//throw new TermServerScriptException("Unable to determine template for " + loincNum);
		//return LoincTemplatedConceptWithDefaultMap.create(loincNum);
		return null;
	}
	
	public void setConcept(Concept c) {
		this.concept = c;
	}

	public Concept getConcept() {
		return concept;
	}
	
	public String getLoincNum() {
		return loincNum;
	}
	
	public String getWrappedId() {
		return loincNum;
	}

	public static void reportStats() throws TermServerScriptException {
		ts.report(MAPPING_DETAIL_TAB, "");
		ts.report(MAPPING_DETAIL_TAB, "Parts mapped", mapped);
		ts.report(MAPPING_DETAIL_TAB, "Parts unmapped", unmapped);
		ts.report(MAPPING_DETAIL_TAB, "Parts skipped", skipped);
		ts.report(MAPPING_DETAIL_TAB, "Unique PartNums mapped", partNumsMapped.size());
		ts.report(MAPPING_DETAIL_TAB, "Unique PartNums unmapped", partNumsUnmapped.size());
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
	
	protected static RelationshipTemplate getAttributeFromDetail(String loincNum, String ldtColumnName) throws TermServerScriptException {
		LoincDetail loincDetail = getLoincDetail(loincNum, ldtColumnName);
		String loincPartNum = loincDetail.getPartNumber();
		if (!loincPartAttributeMap.containsKey(loincPartNum)) {
			throw new TermServerScriptException("Unable to find attribute mapping for " + loincNum + " / " + loincPartNum + " (" + ldtColumnName + ")" );
		}
		//We will change the attribute type from this map, depending on the template, so return a copy
		return loincPartAttributeMap.get(loincPartNum).clone();
	}
	
	protected static RelationshipTemplate getAttributeFromDetailWithType(String loincNum, String ldtColumnName, Concept type) throws TermServerScriptException {
		RelationshipTemplate rt = getAttributeFromDetail(loincNum, ldtColumnName);
		rt.setType(type);
		return rt;
	}
	
	protected boolean addAttributeFromDetailWithType(List<RelationshipTemplate> attributes, String loincNum, String ldtColumnName,
			List<String> issues, Concept type) {
		boolean attributeAdded = false;
		try {
			attributes.add(getAttributeFromDetailWithType(loincNum, ldtColumnName, type));
			attributeAdded = true;
		} catch (TermServerScriptException e) {
			//TODO Stop passing issues around, we have access to the concept here
			issues.add(e.getMessage() + " definition status set to Primitive");
			this.concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		}
		return attributeAdded;
	}
	
	protected boolean addAttributeFromDetail(List<RelationshipTemplate> attributes, String loincNum, String ldtColumnName,
			List<String> issues) {
		boolean attributeAdded = false;
		try {
			attributes.add(getAttributeFromDetail(loincNum, ldtColumnName));
			attributeAdded = true;
		} catch (TermServerScriptException e) {
			//TODO Stop passing issues around, we have access to the concept here
			issues.add(e.getMessage() + " definition status set to Primitive");
			this.concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		}
		return attributeAdded;
	}
	
	

}
