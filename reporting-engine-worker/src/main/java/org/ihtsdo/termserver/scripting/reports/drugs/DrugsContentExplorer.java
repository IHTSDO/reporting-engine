package org.ihtsdo.termserver.scripting.reports.drugs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class DrugsContentExplorer extends TermServerReport implements ReportClass {

	private static final String MP = "MP";
	private static final String MPO = "MPO";
	private static final String MPF = "MPF";
	private static final String MPFO = "MPFO";
	private static final String RMPF = "RMPF";
	private static final String RMPFO = "RMPFO";
	private static final String CD = "CD";
	private static final String RCD = "RCD";
	private static final String PRODUCT = "PRODUCT";

	private enum DrugClass { MP, MPO, MPF, MPFO, RMPF, RMPFO, CD, RCD }

	private static final Logger LOGGER = LoggerFactory.getLogger(DrugsContentExplorer.class);

	private static final String RESTRICT_TO_CLASSES = "Restrict to classes";

	private static final String UNKNOWN = "UNKNOWN";

	private List<String> drugClassesOfInterest = new ArrayList<>();

	private Collection<Concept> allKnownDrugs;

	private String[] defaultDrugClasses;

	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(DrugsContentExplorer.class, args, new HashMap<>());
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1H7T_dqmvQ-zaaRtfrd-T3QCUMD7_K8st"); //Drugs Analysis
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		drugClassesOfInterest = getJobRun().getParameters().getValues(RESTRICT_TO_CLASSES);
		if (drugClassesOfInterest.isEmpty()) {
			drugClassesOfInterest = Arrays.asList(getDefaultDrugClasses());
		}

		allKnownDrugs = MEDICINAL_PRODUCT.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);

		String[] columnHeadings = new String[] {
				"SCTID, FSN, SemTag, Ingredient Count, Count of Base, Expression, Definition Status, Drug Class, MP Descendants, MPO Descendants, MPF Descendants, MPFO Descendants, RMPF Descedants, RMPFO Descendants, CD Descendants, RCD Descendants, Unknown Descendants, MP Ancestors, MPO Ancestors, MPF Ancestors, MPFO Ancestors, RMPF Ancestors, RMPFO Ancestors, CD Ancestors, RCD Ancestors, Unknown Ancestors "
				};
		String[] tabNames = new String[] {	
				"Drugs Content Explored"
				};
		super.postInit(tabNames, columnHeadings);
	}

	private String[] getDefaultDrugClasses() {
		if (defaultDrugClasses == null) {
			defaultDrugClasses = EnumSet.allOf(DrugClass.class).stream().map(DrugClass::name)
					.filter(d -> !d.startsWith("R")).toArray(String[]::new);
		}
		return defaultDrugClasses;
	}

	@Override
	public Job getJob() {
		String[] drugClasses = EnumSet.allOf(DrugClass.class).stream().map(DrugClass::name).toArray(String[]::new);
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.STRING).withDefaultValue(true)
				.add(RESTRICT_TO_CLASSES).withType(JobParameter.Type.CHECKBOXES)
					.withValues(drugClasses)
					.withDefaultValues(constructCheckboxValues(drugClasses))
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Drugs Content Explorer")
				.withDescription("This report lists combinations of dose forms and units along with usage counts and examples.  Also BoSS/PAI/MDF combinations.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	private String[] constructCheckboxValues(String[] drugClasses) {
		String[] checkboxValues = new String[drugClasses.length];
		for (int i = 0; i < drugClasses.length; i++) {
			checkboxValues[i] = isDefaultDrugClass(drugClasses[i]) ? "true" : "false";
		}
		return checkboxValues;
	}

	private boolean isDefaultDrugClass(String drugClass) {
		for (String defaultDrugClass : getDefaultDrugClasses()) {
			if (defaultDrugClass.equals(drugClass)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> conceptsToReport;
		if (StringUtils.isEmpty(subsetECL)) {
			conceptsToReport = new ArrayList<>(allKnownDrugs);
		} else {
			conceptsToReport = new ArrayList<>(findConcepts(subsetECL));
		}

		LOGGER.info("Reporting on {} concepts", conceptsToReport.size());

		conceptsToReport.stream()
				.sorted(Comparator.comparing(Concept::getFsn))
				.filter(this::hasDrugClassOfInterest)
				.forEach(this::analyzeConcept);
	}

	private boolean hasDrugClassOfInterest(Concept c) {
		DrugUtils.setConceptType(c);
		return drugClassesOfInterest.stream()
				.anyMatch(d -> traslateConceptTypeToString(c.getConceptType()).equals(d));
	}

	private String traslateConceptTypeToString(ConceptType conceptType) {
		if (conceptType == null) {
			return UNKNOWN;
		}
		switch(conceptType) {
			case MEDICINAL_PRODUCT: return MP;
			case MEDICINAL_PRODUCT_FORM: return MPF;
			case MEDICINAL_PRODUCT_FORM_ONLY: return MPFO;
			case MEDICINAL_PRODUCT_ONLY: return MPO;
			case REAL_MEDICINAL_PRODUCT_FORM: return RMPF;
			case REAL_MEDICINAL_PRODUCT_FORM_ONLY: return RMPFO;
			case CLINICAL_DRUG: return CD;
			case REAL_CLINICAL_DRUG: return RCD;
			case PRODUCT: return PRODUCT;
			default: throw new IllegalArgumentException("Unexpected concept type: " + conceptType);
		}
	}

	private void analyzeConcept(Concept c) {
		Map<String, Integer> descendantData = new HashMap<>();
		Map<String, Integer> ancestorData = new HashMap<>();
		try {
			c.getDescendants(NOT_SET).stream()
					.map(d -> traslateConceptTypeToString(d.getConceptType()))
					.forEach(s -> descendantData.merge(s, 1, Math::addExact));
			c.getAncestors(NOT_SET).stream()
					.filter(a -> allKnownDrugs.contains(a))
					.map(d -> traslateConceptTypeToString(d.getConceptType()))
					.forEach(s -> ancestorData.merge(s, 1, Math::addExact));
			report(c,
					DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP).size(),
					DrugUtils.getCountOfBaseOrNA(c),
					c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP),
					SnomedUtils.translateDefnStatus(c.getDefinitionStatus()),
					traslateConceptTypeToString(c.getConceptType()),
					descendantData.getOrDefault(MP, 0),
					descendantData.getOrDefault(MPO, 0),
					descendantData.getOrDefault(MPF, 0),
					descendantData.getOrDefault(MPFO, 0),
					descendantData.getOrDefault(RMPF, 0),
					descendantData.getOrDefault(RMPFO, 0),
					descendantData.getOrDefault(CD, 0),
					descendantData.getOrDefault(RCD, 0),
					descendantData.getOrDefault(UNKNOWN, 0),
					ancestorData.getOrDefault(MP, 0),
					ancestorData.getOrDefault(MPO, 0),
					ancestorData.getOrDefault(MPF, 0),
					ancestorData.getOrDefault(MPFO, 0),
					ancestorData.getOrDefault(RMPF, 0),
					ancestorData.getOrDefault(RMPFO, 0),
					ancestorData.getOrDefault(CD, 0),
					ancestorData.getOrDefault(RCD, 0),
					ancestorData.getOrDefault(UNKNOWN, 0));
		} catch (TermServerScriptException e) {
			try {
				report(c, e.getMessage());
			} catch (TermServerScriptException ex) {
				throw new IllegalStateException(ex);
			}
		}
	}
}
