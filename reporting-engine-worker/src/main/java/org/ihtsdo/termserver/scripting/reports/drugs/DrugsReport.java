package org.ihtsdo.termserver.scripting.reports.drugs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public abstract class DrugsReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(DrugsReport.class);

	enum Mode {DRUG, VACCINE}
	protected Mode mode = Mode.DRUG;

	protected static final String ISSUE_COUNTS = "Issue Counts";
	protected static final String RECENT_CHANGES_ONLY = "Recent Changes Only";
	protected static final String INJECTION = "injection";
	protected static final String INFUSION = "infusion";

	protected static final String SEMTAG_PRODUCT = "(product)";

	protected List<Concept> allDrugs;

	protected final Set<Concept> presAttributes = new HashSet<>();
	protected final Set<Concept> concAttributes = new HashSet<>();
	protected final Concept[] doseFormTypes = new Concept[] {HAS_MANUFACTURED_DOSE_FORM};

	protected Map<BaseMDF, Set<RelationshipGroup>> baseMDFMap;
	protected final Map<Concept,Concept> grouperSubstanceUsage = new HashMap<>();

	protected final Concept[] mpValidAttributes = new Concept[] { IS_A, HAS_ACTIVE_INGRED, COUNT_BASE_ACTIVE_INGREDIENT, PLAYS_ROLE };
	protected final Concept[] mpfValidAttributes = new Concept[] { IS_A, HAS_ACTIVE_INGRED, HAS_MANUFACTURED_DOSE_FORM, COUNT_BASE_ACTIVE_INGREDIENT, PLAYS_ROLE };

	protected final Concept [] solidUnits = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	protected final Concept [] liquidUnits = new Concept [] { MILLILITER, LITER };
	protected final String[] semTagHiearchy = new String[] { SEMTAG_PRODUCT, "(medicinal product)", "(medicinal product form)", "(clinical drug)" };

	protected static final String[] badWords = new String[] { "preparation", "agent", "+"};

	protected boolean isRecentlyTouchedConceptsOnly = false;
	protected Set<Concept> recentlyTouchedConcepts;
	protected List<Concept> bannedMpParents;

	protected DoseFormHelper doseFormHelper;
	protected TermGenerator termGenerator;

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3");  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
		getSnapshotConfiguration().setPopulateReleaseFlag(true);
	}

	protected Job getDrugJob(String reportName, String reportDescription) {
		JobParameters params = new JobParameters()
				.add(RECENT_CHANGES_ONLY)
				.withType(JobParameter.Type.BOOLEAN)
				.withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName(reportName)
				.withDescription(reportDescription)
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Details, Details, Details, Further Details", "Issue, Count"};
		String[] tabNames = new String[] {	"Issues", "Summary"};
		postInit(tabNames, columnHeadings);
	}

	@Override
	public void postInit(String[] tabNames, String [] columnHeadings) throws TermServerScriptException {
		super.postInit(tabNames, columnHeadings);
		allDrugs = SnomedUtils.sort(gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT));

		populateGrouperSubstances();
		populateBaseMDFMap();

		doseFormHelper = new DoseFormHelper();
		doseFormHelper.initialise(gl);
		termGenerator = new DrugTermGenerator(this);

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

		bannedMpParents = new ArrayList<>();
		bannedMpParents.add(gl.getConcept("763158003 |Medicinal product (product)|"));
		bannedMpParents.add(gl.getConcept("766779001 |Medicinal product categorized by disposition (product)|"));
		bannedMpParents.add(gl.getConcept("763760008 |Medicinal product categorized by structure (product)|"));
		bannedMpParents.add(gl.getConcept("763087004 |Medicinal product categorized by therapeutic role (product)|"));
	}

	protected void linkReport(DrugsReport drugsReport) {
		//Link this report to an existing running report, so they write to the same structures
		this.setSummaryCountsByCategoryMap(drugsReport.getSummaryCountsByCategoryMap());
		this.allDrugs = drugsReport.allDrugs;
		this.baseMDFMap = drugsReport.baseMDFMap;
		this.reportManager = drugsReport.reportManager;
		this.termGenerator = drugsReport.termGenerator;
		this.doseFormHelper = drugsReport.doseFormHelper;
		this.summaryDetails = drugsReport.summaryDetails;
	}

	private void populateGrouperSubstances() throws TermServerScriptException {
		//DRUGS-793 Ingredients of "(product)" Medicinal products will be
		//considered 'grouper substances' that should not be used as BoSS
		for (Concept c : gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT)) {
			DrugUtils.setConceptType(c);
			if (c.getConceptType().equals(ConceptType.PRODUCT)) {
				for (Concept substance : DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
					grouperSubstanceUsage.putIfAbsent(substance, c);
				}
			}
		}
	}

	private void populateBaseMDFMap() {
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
					baseMDFMap.computeIfAbsent(baseMDF, k -> new HashSet<>()).add(rg);
				}
			}
		}
	}

	protected Concept getMDF(Concept concept) {
		return getMDF(concept, false);
	}

	protected Concept getMDF(Concept concept, boolean allowNull) {
		RelationshipGroup ungrouped = concept.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED);
		return ungrouped == null ? null : ungrouped.getValueForType(HAS_MANUFACTURED_DOSE_FORM, allowNull);
	}

	protected static BaseMDF getBaseMDF(RelationshipGroup rg, Concept mdf) {
		Concept boSS = rg.getValueForType(HAS_BOSS);
		Concept pai =  rg.getValueForType(HAS_PRECISE_INGRED);
		//What is the base of the ingredient
		Set<Concept> ingredBases = Collections.singleton(pai);
		if (!boSS.equals(pai)) {
			ingredBases = DrugUtils.getSubstanceBase(pai, boSS);
		}

		if (ingredBases.size() != 1) {
			LOGGER.debug("Unable to obtain single BoSS from {}",  rg);
			return null;
		} else {
			Concept base = ingredBases.iterator().next();
			return new BaseMDF(base, mdf);
		}
	}

	protected boolean isMP(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) ||
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}

	protected boolean isMPOnly(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}

	protected boolean isMPFOnly(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}

	protected boolean isMPF(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) ||
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}

	protected boolean isCD(Concept concept) {
		return concept.getConceptType().equals(ConceptType.CLINICAL_DRUG);
	}

	@Override
	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		incrementSummaryCount((String)details[0]);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}

	public static class BaseMDF {
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
			if (other instanceof BaseMDF otherBaseMDF) {
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

	static class BoSSPAI {
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
			if (other instanceof BoSSPAI otherBoSSPAI) {
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
