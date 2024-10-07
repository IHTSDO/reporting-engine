package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.TermGenerator;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MP_MPF_Validation extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MP_MPF_Validation.class);

	private List<Concept> allDrugs;
	private static String RECENT_CHANGES_ONLY = "Recent Changes Only";
	
	Concept [] solidUnits = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	Concept [] liquidUnits = new Concept [] { MILLILITER, LITER };
	String[] semTagHiearchy = new String[] { "(product)", "(medicinal product)", "(medicinal product form)", "(clinical drug)" };
	
	private Map<Concept, Boolean> acceptableMpfDoseForms = new HashMap<>();
	private Map<Concept, Boolean> acceptableCdDoseForms = new HashMap<>();	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	private Map<Concept,Concept> grouperSubstanceUsage = new HashMap<>();
	private Map<BaseMDF, Set<RelationshipGroup>> baseMDFMap;
	private List<Concept> bannedMpParents;
	
	private boolean isRecentlyTouchedConceptsOnly = false;
	private Set<Concept> recentlyTouchedConcepts;

	Concept[] mpValidAttributes = new Concept[] { IS_A, HAS_ACTIVE_INGRED, COUNT_BASE_ACTIVE_INGREDIENT, PLAYS_ROLE };
	Concept[] mpfValidAttributes = new Concept[] { IS_A, HAS_ACTIVE_INGRED, HAS_MANUFACTURED_DOSE_FORM, COUNT_BASE_ACTIVE_INGREDIENT, PLAYS_ROLE };
	
	Set<Concept> presAttributes = new HashSet<>();
	Set<Concept> concAttributes = new HashSet<>();
	
	TermGenerator termGenerator = new DrugTermGenerator(this);
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(MP_MPF_Validation.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3");  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Expected Result, Variance, Source, Further Details",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		allDrugs = SnomedUtils.sort(gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT));
		populateAcceptableDoseFormMaps();
		populateGrouperSubstances();
		populateBaseMDFMap();
		
		super.postInit(tabNames, columnHeadings);
		
		presAttributes.add(HAS_PRES_STRENGTH_VALUE);
		presAttributes.add(HAS_PRES_STRENGTH_UNIT);
		presAttributes.add(HAS_PRES_STRENGTH_DENOM_UNIT);
		presAttributes.add(HAS_PRES_STRENGTH_DENOM_VALUE);
		
		concAttributes.add(HAS_CONC_STRENGTH_VALUE);
		concAttributes.add(HAS_CONC_STRENGTH_UNIT);
		concAttributes.add(HAS_CONC_STRENGTH_DENOM_UNIT);
		concAttributes.add(HAS_CONC_STRENGTH_DENOM_VALUE);
		
		bannedMpParents = new ArrayList<>();
		bannedMpParents.add(gl.getConcept("763158003 |Medicinal product (product)|"));
		bannedMpParents.add(gl.getConcept("766779001 |Medicinal product categorized by disposition (product)|"));
		bannedMpParents.add(gl.getConcept("763760008 |Medicinal product categorized by structure (product)|"));
		bannedMpParents.add(gl.getConcept("763087004 |Medicinal product categorized by therapeutic role (product)|"));
		
		if (jobRun.getParamBoolean(RECENT_CHANGES_ONLY)) {
			isRecentlyTouchedConceptsOnly = true;
			recentlyTouchedConcepts = SnomedUtils.getRecentlyTouchedConcepts(gl.getAllConcepts());
		}
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(RECENT_CHANGES_ONLY)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("MP MPF Validation")
				.withDescription("This report checks for a number of potential issues with MP/MPF concepts, as per RP-740.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateDrugsModeling();
		populateSummaryTab();
		LOGGER.info("Summary tab complete, all done.");
	}

	private void validateDrugsModeling() throws TermServerScriptException {
		ConceptType[] allDrugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_ONLY, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.MEDICINAL_PRODUCT_FORM_ONLY, ConceptType.CLINICAL_DRUG };
		double conceptsConsidered = 0;
		//for (Concept c : Collections.singleton(gl.getConcept("776935006"))) {
		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			
			if (c.getFsn().toLowerCase().contains("vaccine")) {
				continue;
			}
			
			DrugUtils.setConceptType(c);
			
			if (isCD(c)) {
				continue;
			}
			
			double percComplete = (conceptsConsidered++/allDrugs.size())*100;
			if (conceptsConsidered%4000==0) {
				LOGGER.info("Percentage Complete " + (int)percComplete);
			}
			
			//INFRA-4159 Seeing impossible situation of no stated parents.  Also DRUGS-895
			if (c.getParents(CharacteristicType.STATED_RELATIONSHIP).size() == 0) {
				String issueStr = "Concept appears to have no stated parents";
				initialiseSummaryInformation(issueStr);
				report(c, issueStr);
				continue;
			}
			
			//DRUGS-629, RP-187
			checkForSemTagViolations(c);
			
			if (isMP(c) || isMPF(c)) {
				//DRUGS-585
				validateNoModifiedSubstances(c);

			}
			
			// DRUGS-281, DRUGS-282, DRUGS-269
			if (!c.getConceptType().equals(ConceptType.PRODUCT)) {
				validateTerming(c, allDrugTypes);
			}
			
			//DRUGS-296 
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) && 
				c.getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next().equals(MEDICINAL_PRODUCT)) {
				validateStatedVsInferredAttributes(c, HAS_ACTIVE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(c, HAS_PRECISE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(c, HAS_MANUFACTURED_DOSE_FORM, allDrugTypes);
			}
			
			//RP-189
			validateProductModellingRules(c);
			
			//DRUGS-288
			validateAttributeValueCardinality(c, HAS_ACTIVE_INGRED);
			
			//RP-175
			validateAttributeRules(c);
		}
		LOGGER.info("Drugs validation complete");
	}

	private void populateGrouperSubstances() throws TermServerScriptException {
		//DRUGS-793 Ingredients of "(product)" Medicinal products will be
		//considered 'grouper substances' that should not be used as BoSS 
		for (Concept c : gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT)) {
			DrugUtils.setConceptType(c);
			if (c.getConceptType().equals(ConceptType.PRODUCT)) {
				for (Concept substance : DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
					if (!grouperSubstanceUsage.containsKey(substance)) {
						grouperSubstanceUsage.put(substance, c);
					}
				}
			}
		}
	}
	
	private void populateBaseMDFMap() throws TermServerScriptException {
		baseMDFMap = new HashMap<>();
		for (Concept c : allDrugs) {
			DrugUtils.setConceptType(c);
			if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				Concept mdf = getMDF(c);
				for (RelationshipGroup rg : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
					//Skip the ungrouped concepts, we're only interested in groups featuring an ingredient
					if (!rg.isGrouped()) {
						continue;
					}
					BaseMDF baseMDF = getBaseMDF(rg, mdf);
					Set<RelationshipGroup> groups = baseMDFMap.get(baseMDF);
					if (groups == null) {
						groups = new HashSet<RelationshipGroup>();
						baseMDFMap.put(baseMDF, groups);
					}
					groups.add(rg);
				}
			}
		}
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(e -> e.getValue())
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
	}

	private void validateNoModifiedSubstances(Concept c) throws TermServerScriptException {
		String issueStr = c.getConceptType() + " has modified ingredient";
		initialiseSummary(issueStr);
		//Check all ingredients for any that themselves have modification relationships
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(HAS_PRECISE_INGRED) || r.getType().equals(HAS_ACTIVE_INGRED) ) {
				Concept ingredient = r.getTarget();
				for (Relationship ir :  ingredient.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (ir.getType().equals(IS_MODIFICATION_OF)) {
						report(c, issueStr, ingredient, "is modification of", ir.getTarget());
					}
				}
			}
		}
	}

	private void validateProductModellingRules(Concept c) throws TermServerScriptException {
		String issueStr = "Product has more than one manufactured dose form attribute in Inferred Form";
		String issueStr2 = "Product has more than one manufactured dose form attribute in Stated Form";
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		Concept targetType = gl.getConcept("411116001 |Has manufactured dose form (attribute)|");
		if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, targetType, ActiveState.ACTIVE).size() > 1) {
			report(c, issueStr);
		}
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, targetType, ActiveState.ACTIVE).size() > 1) {
			report(c, issueStr2);
		}
	}

	private void validateStatedVsInferredAttributes(Concept concept,
			Concept attributeType, ConceptType[] drugTypes) throws TermServerScriptException {
		String issueStr = "Cardinality mismatch stated vs inferred " + attributeType;
		String issue2Str = "Stated X is not present in inferred view";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		if (drugTypes==null || SnomedUtils.isConceptType(concept, drugTypes)) {
			Set<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			Set<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				report(concept, issueStr, data);
			} else {
				for (Relationship statedAttribute : statedAttributes) {
					boolean found = false;
					for (Relationship infAttribute : infAttributes) {
						if (statedAttribute.getTarget().equals(infAttribute.getTarget())) {
							found = true;
							break;
						}
					}
					if (!found) {
						issue2Str = "Stated " + statedAttribute.getType() + " is not present in inferred view";
						String data = statedAttribute.toString();
						report(concept, issue2Str, data);
					}
				}
			}
		}
	}
		
	private BaseMDF getBaseMDF(RelationshipGroup rg, Concept mdf) {
		Concept boSS = rg.getValueForType(HAS_BOSS);
		Concept pai =  rg.getValueForType(HAS_PRECISE_INGRED);
		//What is the base of the ingredient
		Set<Concept> ingredBases = Collections.singleton(pai);
		if (!boSS.equals(pai)) {
			ingredBases = DrugUtils.getSubstanceBase(pai, boSS);
		}
		
		if (ingredBases.size() != 1) {
			LOGGER.debug("Unable to obtain single BoSS from " + rg.toString());
			return null;
		} else {
			Concept base = ingredBases.iterator().next();
			return new BaseMDF(base, mdf);
		}
	}
	
	private Concept getMDF(Concept concept) {
		return getMDF(concept, false);
	}
	
	private Concept getMDF(Concept concept, boolean allowNull) {
		RelationshipGroup ungrouped = concept.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED);
		return ungrouped == null ? null : ungrouped.getValueForType(HAS_MANUFACTURED_DOSE_FORM, allowNull);
	}

	private void validateTerming(Concept c, ConceptType[] drugTypes) throws TermServerScriptException {
		//Only check FSN for certain drug types (to be expanded later)
		if (!SnomedUtils.isConceptType(c, drugTypes)) {
			incrementSummaryInformation("Concepts ignored - wrong type");
		}
		incrementSummaryInformation("Concepts validated to ensure ingredients correct in FSN");
		Description currentFSN = c.getFSNDescription();
		termGenerator.setQuiet(true);
		
		//Create a clone to be retermed, and then we can compare descriptions with the original	
		Concept clone = c.clone();
		termGenerator.ensureTermsConform(null, clone, CharacteristicType.STATED_RELATIONSHIP);
		Description proposedFSN = clone.getFSNDescription();
		compareTerms(c, "FSN", currentFSN, proposedFSN);
		Description ptUS = clone.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description ptGB = clone.getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (ptUS == null || ptUS.getTerm() == null || ptGB == null || ptGB.getTerm() == null) {
			LOGGER.debug("Debug here - hit a null");
		}
		if (ptUS.getTerm().equals(ptGB.getTerm())) {
			compareTerms(c, "PT", c.getPreferredSynonym(US_ENG_LANG_REFSET), ptUS);
		} else {
			compareTerms(c, "US-PT", c.getPreferredSynonym(US_ENG_LANG_REFSET), ptUS);
			compareTerms(c, "GB-PT", c.getPreferredSynonym(GB_ENG_LANG_REFSET), ptGB);
		}
	}
	
	private void checkForSemTagViolations(Concept c) throws TermServerScriptException {
		String issueStr =  "Has higher level semantic tag than parent";
		String issueStr2 = "Has semantic tag incompatible with that of parent";
		//String issueStr3 = "Has incorrect parent";
		String issueStr4 = "Has parent with an incompatible semantic tag incompatible with that of parent";
		String issueStr5 = "Has invalid parent / semantic tag combination";
		String issueStr6 = "MPF-Only expected to have MPF (not only) and MP-Only as parents";
		
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		//initialiseSummary(issueStr3);
		initialiseSummary(issueStr4);
		initialiseSummary(issueStr5);
		initialiseSummary(issueStr6);
		
		//Ensure that the hierarchical level of this semantic tag is the same or deeper than those of the parent
		int tagLevel = getTagLevel(c);
		for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int parentTagLevel = getTagLevel(p);
			if (tagLevel < parentTagLevel) {
				report(c, issueStr, p);
			}
		}
		
		if (isMPOnly(c)) {
			validateParentSemTags(c, "(medicinal product)", issueStr2, true);
		} else if (isMPFOnly(c)) {
			//Complex one this.   An MPF-Only should have at least one parent which is an MPF (not only)
			//and at least one which is MP-Only. And no other parents.
			boolean hasMpfNotOnly = false;
			boolean hasMpOnly = false;
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (isMPF(parent) && !isMPFOnly(parent)) {
					hasMpfNotOnly = true;
				} else if (isMPOnly(parent)) {
					hasMpOnly = true;
				} else {
					report(c, issueStr6, parent);
					break;
				}
			}
			if (!hasMpfNotOnly && !hasMpOnly) {
				report(c, issueStr6, getParentsJoinedStr(c));
			}
		} 
		
		validateParentTagCombo(c, gl.getConcept("766779001 |Medicinal product categorized by disposition (product)|"), "(product)", issueStr5);
		validateParentTagCombo(c, gl.getConcept("763760008 |Medicinal product categorized by structure (product)| "), "(product)", issueStr5);
	}
	

	private int getTagLevel(Concept c) throws TermServerScriptException {
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
		for (int i=0; i < semTagHiearchy.length; i++) {
			if (semTagHiearchy[i].equals(semTag)) {
				return i;
			}
		}
		//throw new TermServerScriptException("Unable to find semantic tag level for: " + c);
		LOGGER.error("Unable to find semantic tag level for: " + c, (Exception)null);
		return NOT_SET;
	}

	private String getParentsJoinedStr(Concept c) {
		return c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream()
				.map(p -> p.getFsn())
				.collect(Collectors.joining(", \n"));
	}
	
	private void validateParentSemTags(Concept c, String requiredTag, String issueStr, boolean anyParentAcceptable) throws TermServerScriptException {
		boolean anyAcceptableParentFound = false;
		List<String[]> failuresToReport = new ArrayList<>();
		
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			String semTag = SnomedUtilsBase.deconstructFSN(parent.getFsn())[1];
			
			//RP-587 There is a case where a CD grouper exists eg for infusion and/or injection
			//Look for and/or in the parent's dose form
			Concept parentDoseForm = getMDF(parent, true);
			
			//This can fail where a concept might have an extra unwanted "product" parent
			//eg 1172826001 |Product containing precisely ambroxol acefyllinate 100 milligram/1 each conventional release oral capsule (clinical drug)|
			if (parentDoseForm != null && parentDoseForm.getFsn().contains("and/or")) {
				continue;
			}
			
			if (!semTag.equals(requiredTag)) {
				if (!anyParentAcceptable) {
					report(c, issueStr, "parent", parent.getFsn(), " expected tag", requiredTag);
				} else {
					failuresToReport.add(new String[] {parent.getFsn(), " expected tag", requiredTag});
				}
			} else {
				anyAcceptableParentFound = true;
			}
		}
		
		if (anyParentAcceptable && !anyAcceptableParentFound) {
			for (String[] params : failuresToReport) {
				report(c, issueStr, "parent", params);
			}
		}
	}

	private void validateParentTagCombo(Concept c, Concept targetParent, 
			String targetSemtag, String issueStr) throws TermServerScriptException {
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (parent.equals(targetParent) && !semTag.equals(targetSemtag)) {
				report(c, issueStr, "Has parent " + targetParent, "but not expected semtag " + targetSemtag);
			}
		}
	}
	
	private void compareTerms(Concept c, String termName, Description actual, Description expected) throws TermServerScriptException {
		String issueStr = "Incorrect " + termName;
		String issue2Str = "Incorrect " + termName + " case significance";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		if (!actual.getTerm().equals(expected.getTerm())) {
			String differences = findDifferences (actual.getTerm(), expected.getTerm());
			report(c, issueStr, expected.getTerm(), differences, actual);
		} else if (!actual.getCaseSignificance().equals(expected.getCaseSignificance())) {
			String detail = "Expected: " + SnomedUtils.translateCaseSignificanceFromEnum(expected.getCaseSignificance());
			detail += ", Actual: " + SnomedUtils.translateCaseSignificanceFromEnum(actual.getCaseSignificance());
			report(c, issue2Str, detail, actual);
		}
	}

	private String findDifferences(String actual, String expected) {
		String differences = "";
		//For each word, see if it exists in the other 
		String[] actuals = actual.split(" ");
		String[] expecteds = expected.split(" ");
		int maxLoop = (actuals.length>expecteds.length)?actuals.length:expecteds.length;
		for (int x=0; x < maxLoop; x++) {
			if (actuals.length > x) {
				if (!expected.contains(actuals[x])) {
					differences += "\"" + actuals[x] + "\" vs \"";
				}
			}
			
			if (expecteds.length > x) {
				if (!actual.contains(expecteds[x])) {
					differences += expecteds[x] + "\" ";
				}
			}
		}
		return differences;
	}

	private void validateAttributeValueCardinality(Concept concept, Concept activeIngredient) throws TermServerScriptException {
		checkforRepeatedAttributeValue(concept, CharacteristicType.INFERRED_RELATIONSHIP, activeIngredient);
		checkforRepeatedAttributeValue(concept, CharacteristicType.STATED_RELATIONSHIP, activeIngredient);
	}

	private void checkforRepeatedAttributeValue(Concept c, CharacteristicType charType, Concept activeIngredient) throws TermServerScriptException {
		String issueStr = "Multiple " + charType + " instances of active ingredient";
		initialiseSummary(issueStr);
		
		Set<Concept> valuesEncountered = new HashSet<Concept>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				report(c, issueStr, target.toString());
			}
			valuesEncountered.add(target);
		}
	}

	
	private void validateAttributeRules(Concept c) throws TermServerScriptException {
		String issueStr =  "MP/MPF must have one or more 'Has active ingredient' attributes";
		initialiseSummary(issueStr);
		if ((isMP(c) || isMPF(c)) && 
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE).size() < 1) {
			report(c, issueStr);
		}
		
		issueStr =  "MP/MPF must not feature any role groups";
		//We mean traditional role groups here, so filter out self grouped
		initialiseSummary(issueStr);
		if ((isMP(c))) {
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
				if (g.getGroupId() == UNGROUPED) {
					continue;
				}
				if (g.size() == 1 && g.getRelationships().iterator().next().getType().equals(HAS_ACTIVE_INGRED)) {
					continue;
				}
				report(c, issueStr, g);
			}
		}
		
		issueStr =  "MPF must feature exactly 1 dose form";
		initialiseSummary(issueStr);
		if ((isMPF(c)) && 
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE).size() != 1) {
			report(c, issueStr);
		}
		
		issueStr = "Incorrect attribute type used";
		if (isMP(c) || isMPF(c)) {
			Concept[] allowedAttributes = isMP(c) ? mpValidAttributes : mpfValidAttributes;
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				//Is this relationship type allowed?
				boolean allowed = false;
				for (Concept allowedType : allowedAttributes) {
					if (allowedType.equals(r.getType())) {
						allowed = true;
					}
				}
				if (!allowed) {
					report(c, issueStr, r);
				}
			}
		}
		
		issueStr =  "MP/MPF must feature 'containing' or 'only' in the FSN and PT";
		initialiseSummary(issueStr);
		if (isMP(c) || isMPF(c)) { 
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.isPreferred()) {
					if (!d.getTerm().contains("containing") && !d.getTerm().contains("only")) {
						report(c, issueStr, d);
					}
				}
			}
		}
		
		issueStr = "MP/MPF must feature exactly one count of base";
		initialiseSummary(issueStr);
		if ((isMPOnly(c) || isMPFOnly(c))
			&& c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) { 
			report(c, issueStr);
		}
		
		//In the case of a missing count of base, we will not have detected that this concept is MP/MPF Only
		//So we must fall back to using fsn lexical search
		issueStr = "'Only' and 'precisely' must have a count of base";
		initialiseSummary(issueStr);
		if ((isMP(c)) && 
				(c.getFsn().contains("only") || c.getFsn().contains("precisely")) &&
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) {
			report(c, issueStr);
		}
	
	}
		
	boolean isPresAttribute(Concept type) {
		return presAttributes.contains(type);
	}
	
	boolean isConcAttribute(Concept type) {
		return concAttributes.contains(type);
	}
	
	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	protected boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}
	
	private void populateAcceptableDoseFormMaps() throws TermServerScriptException {
		String fileName = "resources/acceptable_dose_forms.tsv";
		LOGGER.debug("Loading {}", fileName);
		try {
			List<String> lines = Files.readLines(new File(fileName), Charsets.UTF_8);
			boolean isHeader = true;
			for (String line : lines) {
				String[] items = line.split(TAB);
				if (!isHeader) {
					Concept c = gl.getConcept(items[0]);
					acceptableMpfDoseForms.put(c, items[2].equals("yes"));
					acceptableCdDoseForms.put(c, items[3].equals("yes"));
				} else {
					isHeader = false;
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
	}
	

	private boolean isMP(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}
	
	private boolean isMPOnly(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}
	
	private boolean isMPFOnly(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}
	
	private boolean isMPF(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}
	
	private boolean isCD(Concept concept) {
		return concept.getConceptType().equals(ConceptType.CLINICAL_DRUG);
	}
	
	class BaseMDF {
		Concept baseSubstance;
		Concept pharmDoseForm;
		int hashCode;
		
		public BaseMDF (Concept baseSubstance, Concept pharmDoseForm) {
			this.baseSubstance = baseSubstance;
			this.pharmDoseForm = pharmDoseForm;
			hashCode = (baseSubstance.toString() + pharmDoseForm.toString()).hashCode();
		}
		
		@Override
		public boolean equals (Object other) {
			if (other instanceof BaseMDF) {
				BaseMDF otherBaseMDF = (BaseMDF)other;
				return this.baseSubstance.equals(otherBaseMDF.baseSubstance) && this.pharmDoseForm.equals(otherBaseMDF.pharmDoseForm);
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
		
		@Override
		public String toString() {
			return baseSubstance.toStringPref() + " as " + pharmDoseForm.toStringPref();
		}
	}
	
	class BoSSPAI {
		Concept boSS;
		Concept pai;
		int hashCode;
		
		public BoSSPAI (Concept boSS, Concept pai) {
			this.boSS = boSS;
			this.pai = pai;
			hashCode = (boSS.toString() + pai.toString()).hashCode();
		}
		
		@Override
		public boolean equals (Object other) {
			if (other instanceof BoSSPAI) {
				BoSSPAI otherBoSSPAI = (BoSSPAI)other;
				return this.boSS.equals(otherBoSSPAI.boSS) && this.pai.equals(otherBoSSPAI.pai);
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
		
		@Override
		public String toString() {
			return  boSS.toStringPref() + " / " + pai.toStringPref();
		}
	}
}
