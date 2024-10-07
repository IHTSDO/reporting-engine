package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaccineValidation extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(VaccineValidation.class);

	private List<Concept> allDrugs;
	private static final String RECENT_CHANGES_ONLY = "Recent Changes Only";
	
	String[] semTagHiearchy = new String[] { "(product)", "(medicinal product)", "(medicinal product form)", "(clinical drug)" };
	
	private static final String[] badWords = new String[] { "preparation", "agent", "+"};

	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	private Map<Concept,Concept> grouperSubstanceUsage = new HashMap<>();

	private List<Concept> bannedMpParents;

	private boolean isRecentlyTouchedConceptsOnly = false;
	private Set<Concept> recentlyTouchedConcepts;
	
	Concept[] mpValidAttributes = new Concept[] { IS_A, HAS_ACTIVE_INGRED, COUNT_BASE_ACTIVE_INGREDIENT, PLAYS_ROLE };
	TermGenerator termGenerator = new VaccineTermGenerator(this);

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "false");
		TermServerScript.run(VaccineValidation.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3");  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Details, Details, Details, Further Details",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		allDrugs = SnomedUtils.sort(gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT));
		populateGrouperSubstances();
		super.postInit(tabNames, columnHeadings);
		
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
				.withName("Vaccine Validation")
				.withDescription("This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy.  No longer used.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateDrugsModeling();
		valiadteTherapeuticRole();
		populateSummaryTab();
		LOGGER.info("Summary tab complete, all done.");
	}

	private void validateDrugsModeling() throws TermServerScriptException {
		ConceptType[] allDrugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_ONLY, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.MEDICINAL_PRODUCT_FORM_ONLY, ConceptType.CLINICAL_DRUG };
		double conceptsConsidered = 0;
		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			
			if (!c.getFsn().toLowerCase().contains("vaccine")) {
				continue;
			}
			
			DrugUtils.setConceptType(c);
			
			double percComplete = (conceptsConsidered++/allDrugs.size())*100;
			if (conceptsConsidered%4000==0) {
				LOGGER.info("Percentage Complete {}", (int)percComplete);
			}

			if (isCD(c) || isMPF(c) || isMPFOnly(c)) {
				String issueStr = "Vaccines should only exist at MP/MPO level";
				initialiseSummaryInformation(issueStr);
				report(c, issueStr);
				continue;
			}
			
			//INFRA-4159 Seeing impossible situation of no stated parents.  Also DRUGS-895
			if (c.getParents(CharacteristicType.STATED_RELATIONSHIP).isEmpty()) {
				String issueStr = "Concept appears to have no stated parents";
				initialiseSummaryInformation(issueStr);
				report(c, issueStr);
				continue;
			}

			// DRUGS-281, DRUGS-282, DRUGS-269
			if (!c.getConceptType().equals(ConceptType.PRODUCT)) {
				validateTerming(c, allDrugTypes);
			}

			validateNoModifiedSubstances(c);
			
			//DRUGS-296 
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) && 
				c.getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next().equals(MEDICINAL_PRODUCT)) {
				validateStatedVsInferredAttributes(c, HAS_ACTIVE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(c, HAS_PRECISE_INGRED, allDrugTypes);
			}

			//RP-189
			validateProductModellingRules(c);
			

			if (SnomedUtils.isConceptType(c, allDrugTypes)) {
				//RP-191
				ensureStatedInferredAttributesEqual(c);
				
				//RP-194, RP-484
				checkForPrimitives(c);
			}
			
			//DRUGS-288
			validateAttributeValueCardinality(c, HAS_ACTIVE_INGRED);
			
			//DRUGS-93, DRUGS-759, DRUGS-803
			checkForBadWords(c);
			
			//DRUGS-629, RP-187
			checkForSemTagViolations(c);
			
			//RP-175
			validateAttributeRules(c);
			
		}
		LOGGER.info("Drugs validation complete");
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

	private void checkForPrimitives(Concept c) throws TermServerScriptException {
		String issueStr = "Primitive concept";
		initialiseSummary(issueStr);
		if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			report(c, issueStr);
		}
	}

	private void ensureStatedInferredAttributesEqual(Concept c) throws TermServerScriptException {
		//Get all stated and inferred relationships and remove ISA and PlaysRole
		//Before checking for equivalence
		String issueStr = "Stated attributes not identical to inferred";
		initialiseSummary(issueStr);
		Set<Relationship> statedAttribs = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		Set<Relationship> inferredAttribs = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		
		Relationship isA = new Relationship(IS_A, null);
		removeRels(isA, statedAttribs, true); //remove all instances
		removeRels(isA, inferredAttribs, true);
		
		Relationship playsRole = new Relationship(PLAYS_ROLE, null);
		removeRels(playsRole, statedAttribs, true); //remove all instances
		removeRels(playsRole, inferredAttribs, true);
		
		//Now loop through all the stated relationship and remove them from inferred.
		//The should all successfully remove, and the inferred rels should be empty at the end.
		for (Relationship r : statedAttribs) {
			boolean success = removeRels(r, inferredAttribs, false); //Just remove one
			if (!success) {
				report(c, issueStr, r);
			}
		}
		
		if (!inferredAttribs.isEmpty()) {
			report(c, issueStr, inferredAttribs.iterator().next());
		}
	}

	private boolean removeRels(Relationship removeMe, Set<Relationship> rels, boolean removeAll) {
		Set<Relationship> forRemoval = new HashSet<>();
		for (Relationship r : rels) {
			if (r.getType().equals(removeMe.getType()) &&
				(removeMe.getTarget() == null || r.equalsTargetOrValue(removeMe))){
				forRemoval.add(r);
				if (!removeAll) {
					break;
				}
			}
		}
		rels.removeAll(forRemoval);
		return !forRemoval.isEmpty();
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
	

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(Map.Entry::getValue)
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
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

	/*
	Need to identify and update:
		FSN beginning with "Product containing" that includes any of the following in any active description:
		agent
		+
		preparation
		product (except in the semantic tag)
	 */
	private void checkForBadWords(Concept concept) throws TermServerScriptException {
		String issueStr2 = "Non-FSN starts with 'Product'";
		initialiseSummary(issueStr2);
		//Check if we're product containing and then look for bad words
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtilsBase.deconstructFSN(term)[0];
			} else if (term.startsWith("Product")) {
				report(concept, issueStr2, d);
			}
			
			for (String badWord : badWords ) {
				String issueStr = "Term contains bad word: " + badWord;
				initialiseSummary(issueStr);
				if (term.contains(badWord)) {
					//Exception, MP PT will finish with word "product"
					if (concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && d.isPreferred() && badWord.equals("product")) {
						continue;
					} else {
						if (badWord.equals("+") && isPlusException(term)) {
							continue;
						}
						report(concept, issueStr, d.toString());
						return;
					}
				}
			}
		}
	}

	private boolean isPlusException(String term) {
		//Various rules that allow a + to exist next to other characters
		if (term.contains("^+") ||
			term.contains("+)") ||
			term.contains("+]") ||
			term.contains("(+")) {
			return true;
		}
		return false;
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
	
	private void compareTerms(Concept c, String termName, Description actual, Description expected) throws TermServerScriptException {
		String issueStr = termName + " does not meet expectations";
		String issue2Str = termName + " case significance does not meet expectations";
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
				if (! expected.contains(actuals[x])) {
					differences += actuals[x] + " vs ";
				}
			}
			
			if (expecteds.length > x) {
				if (! actual.contains(expecteds[x])) {
					differences += expecteds[x] + " ";
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
		
		Set<Concept> valuesEncountered = new HashSet<>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				report(c, issueStr, target.toString());
			}
			valuesEncountered.add(target);
		}
	}

	private void checkForSemTagViolations(Concept c) throws TermServerScriptException {
		String issueStr =  "Has higher level semantic tag than parent";
		String issueStr2 = "Has semantic tag incompatible with that of parent";
		String issueStr3 = "Has prohibited parent";
		String issueStr4 = "Has parent with an incompatible semantic tag incompatible with that of parent";
		String issueStr5 = "Has invalid parent / semantic tag combination";
		String issueStr6 = "MPF-Only expected to have MPF (not only) and MP-Only as parents";
		
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		initialiseSummary(issueStr3);
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
			validateParentSemTags(c, "(medicinal product)", issueStr2);
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
		
		if (isMP(c)) {
			checkForBannedParents(c, issueStr3);
			if (c.getFsn().contains("contains")) {
				validateParentSemTags(c, "(product)", issueStr4);
			}
		}
		
		validateParentTagCombo(c, gl.getConcept("766779001 |Medicinal product categorized by disposition (product)|"), "(product)", issueStr5);
		validateParentTagCombo(c, gl.getConcept("763760008 |Medicinal product categorized by structure (product)| "), "(product)", issueStr5);
	}

	private String getParentsJoinedStr(Concept c) {
		return c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream()
				.map(Concept::getFsn)
				.collect(Collectors.joining(", \n"));
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

	private void validateParentSemTags(Concept c, String requiredTag, String issueStr) throws TermServerScriptException {
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
				report(c, issueStr, "parent", parent.getFsn(), " expected tag", requiredTag);
			}
		}
	}
	
	private void checkForBannedParents(Concept c, String issueStr) throws TermServerScriptException {
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			for (Concept bannedParent : bannedMpParents) {
				if (parent.equals(bannedParent)) {
					report(c, issueStr, parent);
				}
			}
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
		if ((isMP(c) || isMPF(c))) {
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

		issueStr = "Unexpected attribute type used";
		if (isMP(c)) {
			Concept[] allowedAttributes = mpValidAttributes;
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

		issueStr = "Precise MP/MPF must feature exactly one count of base";
		initialiseSummary(issueStr);
		if ((isMPOnly(c) || isMPFOnly(c))
			&& c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) { 
			report(c, issueStr);
		}
		
		//In the case of a missing count of base, we will not have detected that this concept is MP/MPF Only
		//So we must fall back to using fsn lexical search
		issueStr = "'Only' and 'precisely' must have a count of base";
		initialiseSummary(issueStr);
		if ((isMP(c) || isMPF(c)) &&
				(c.getFsn().contains("only") || c.getFsn().contains("precisely")) &&
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) {
			report(c, issueStr);
		}

	}

	private int getTagLevel(Concept c) {
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
		for (int i=0; i < semTagHiearchy.length; i++) {
			if (semTagHiearchy[i].equals(semTag)) {
				return i;
			}
		}
		LOGGER.error("Unable to find semantic tag level for: {}", c, (Exception)null);
		return NOT_SET;
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
	
	//RP-198
	private void valiadteTherapeuticRole() throws TermServerScriptException {
		String issueStr = "Descendant of therapeutic role should not be 'agent'";
		initialiseSummary(issueStr);
		Concept theraputicRole = gl.getConcept("766941000 |Therapeutic role (role)|");
		nextConcept:
		for (Concept c : theraputicRole.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().toLowerCase().contains("agent")) {
					report(c, issueStr, d);
					continue nextConcept;
				}
			}
		}
	}
	

}
