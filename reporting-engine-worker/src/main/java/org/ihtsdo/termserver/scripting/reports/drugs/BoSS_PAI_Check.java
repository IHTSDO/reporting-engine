package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoSS_PAI_Check extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(BoSS_PAI_Check.class);

	private List<Concept> allDrugs;
	private static String RECENT_CHANGES_ONLY = "Recent Changes Only";
	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	private Map<Concept,Concept> grouperSubstanceUsage = new HashMap<>();
	private Map<BaseMDF, Set<RelationshipGroup>> baseMDFMap;
	private Set<BaseMDF> reportedBaseMDFCombos = new HashSet<>();
	
	private boolean isRecentlyTouchedConceptsOnly = false;
	private Set<Concept> recentlyTouchedConcepts;
	
	Set<Concept> presAttributes = new HashSet<>();
	Set<Concept> concAttributes = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(BoSS_PAI_Check.class, args, params);
	}
	
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
				.withName("BoSS-PAI Validation")
				.withDescription("This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy around the Basis of Strength Substance and the Precise Active Ingredient.")
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
		double conceptsConsidered = 0;
		//for (Concept c : Arrays.asList(gl.getConcept("1153521005"), gl.getConcept("1230256001"))) {
		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			
			DrugUtils.setConceptType(c);
			
			double percComplete = (conceptsConsidered++/allDrugs.size())*100;
			if (conceptsConsidered%4000==0) {
				LOGGER.info("Percentage Complete " + (int)percComplete);
			}
			
			//DRUGS-267
			validateIngredientsAgainstBoSS(c);
			//DRUGS-1021
			if (isCD(c)) {
				checkBossPaiPdfCombinations(c);
			}
			
			//DRUGS-793
			if (!c.getConceptType().equals(ConceptType.PRODUCT)) {
				checkForBossGroupers(c);
				checkForPaiGroupers(c);
			}
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
	
	private void checkForBossGroupers(Concept c) throws TermServerScriptException {
		String issueStr = "Grouper substance used as BoSS";
		initialiseSummary(issueStr);
		for (Concept boss : SnomedUtils.getTargets(c, new Concept[] {HAS_BOSS}, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (grouperSubstanceUsage.containsKey(boss)) {
				report(c, issueStr, boss, " identified as grouper in ", grouperSubstanceUsage.get(boss));
			}
		}
	}
	
	private void checkForPaiGroupers(Concept c) throws TermServerScriptException {
		String issueStr = "Grouper substance used as PAI";
		initialiseSummary(issueStr);
		for (Concept pai : SnomedUtils.getTargets(c, new Concept[] {HAS_PRECISE_INGRED}, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (grouperSubstanceUsage.containsKey(pai)) {
				report(c, issueStr, pai, " identified as grouper in ", grouperSubstanceUsage.get(pai));
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

	private void validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		String issueStr  = "Active ingredient is a subtype of BoSS.  Expected modification.";
		String issue2Str = "Basis of Strength not equal or subtype of active ingredient, neither is active ingredient a modification of the BoSS";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		Set<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_BOSS, ActiveState.ACTIVE);
		//Check BOSS attributes against active ingredients - must be in the same relationship group
		Set<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			incrementSummaryInformation("BoSS attributes checked");
			boolean matchFound = false;
			Concept boSS = bRel.getTarget();
			for (Relationship iRel : ingredientRels) {
				Concept ingred = iRel.getTarget();
				if (bRel.getGroupId() == iRel.getGroupId()) {
					boolean isSelf = boSS.equals(ingred);
					boolean isSubType = gl.getDescendantsCache().getDescendants(boSS).contains(ingred);
					boolean isModificationOf = DrugUtils.isModificationOf(ingred, boSS);
					
					if (isSelf || isSubType || isModificationOf) {
						matchFound = true;
						if (isSubType) {
							incrementSummaryInformation("Active ingredient is a subtype of BoSS");
							report(concept, issueStr, ingred, boSS);
						} else if (isModificationOf) {
							incrementSummaryInformation("Valid Ingredients as Modification of BoSS");
						} else if (isSelf) {
							incrementSummaryInformation("BoSS matches ingredient");
						}
					}
				}
			}
			if (!matchFound) {
				report(concept, issue2Str, boSS);
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
	
	private void checkBossPaiPdfCombinations(Concept concept) throws TermServerScriptException {
		String issueStr  = "BoSS-PAI combination differs";
		initialiseSummary(issueStr);
		
		for (RelationshipGroup rg : concept.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Have we already reported this role group for a BossPAI violation?
			if (!rg.isGrouped() /*|| reportedForBoSSPAIViolation.contains(rg)*/) {
				continue;
			}
			//What is this BaseMDF?  Find all other RelGroups that have that same base and pharm dose form
			Concept mdf = getMDF(concept);
			BaseMDF baseMDF = getBaseMDF(rg, mdf);
			
			if (baseMDF == null) {
				LOGGER.debug("Failed to obtain baseMDF in " + concept);
				continue;
			}
			
			if (reportedBaseMDFCombos.contains(baseMDF)) {
				continue;
			}
			
			Concept boSS = rg.getValueForType(HAS_BOSS);
			Concept pai =  rg.getValueForType(HAS_PRECISE_INGRED);
			BoSSPAI boSSPAI = new BoSSPAI(boSS, pai);
			Set<RelationshipGroup> relGroups = baseMDFMap.get(baseMDF);
			if (relGroups == null) {
				LOGGER.debug("Unable to find stored relGroups against " + baseMDF + " from " + concept);
			} else {
				String mismatchingDetails = "";
				Set<BoSSPAI> bossPAIcombosReported = new HashSet<>();
				for (RelationshipGroup rg2 : relGroups) {
					//Now do we also match on Boss & PAI?
					Concept boSS2 = rg2.getValueForType(HAS_BOSS);
					Concept pai2 =  rg2.getValueForType(HAS_PRECISE_INGRED);
					BoSSPAI boSSPAI2 = new BoSSPAI(boSS2, pai2);
					if (bossPAIcombosReported.contains(boSSPAI2)) {
						continue;
					}
					if (!boSS.equals(boSS2) || !pai.equals(pai2)) {
						if (mismatchingDetails.length() > 0) {
							mismatchingDetails += "\n";
						} else {
							//First time through, add the original boSSPAI as well as the matching one
							mismatchingDetails += boSSPAI + "\n";
						}
						mismatchingDetails += boSSPAI2 + " eg " + rg2.getSourceConcept().toStringPref();
						bossPAIcombosReported.add(boSSPAI2);
					}
				}
				if (mismatchingDetails.length() > 0) {
					report(concept, issueStr, baseMDF, mismatchingDetails);
					reportedBaseMDFCombos.add(baseMDF);
				}
			}
		}
	}
	
	private Concept getMDF(Concept concept) {
		return getMDF(concept, false);
	}
	
	private Concept getMDF(Concept concept, boolean allowNull) {
		RelationshipGroup ungrouped = concept.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED);
		return ungrouped == null ? null : ungrouped.getValueForType(HAS_MANUFACTURED_DOSE_FORM, allowNull);
	}

	boolean isPresAttribute(Concept type) {
		return presAttributes.contains(type);
	}
	
	boolean isConcAttribute(Concept type) {
		return concAttributes.contains(type);
	}

	@Override
	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}

	@Override
	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
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
			return baseSubstance.toStringPref() + " with dose form " + pharmDoseForm.toStringPref();
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
