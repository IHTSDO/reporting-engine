package org.ihtsdo.termserver.scripting.reports.drugs;

import com.google.common.util.concurrent.AtomicLongMap;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DrugsContentExplorer extends TermServerReport implements ReportClass {

	private enum DrugClass { MP, MPO, MPF, MPFO, RMPF, RMPFO, CD, RCD };

	private static final Logger LOGGER = LoggerFactory.getLogger(DrugsContentExplorer.class);

	private static String RESTRICT_TO_CLASSES = "Restrict to classes";

	private List<String> drugClassesOfInterest = new ArrayList<>();

	private Collection<Concept> allKnownDrugs;

	private String[] defaultDrugClasses;
	{
		defaultDrugClasses = EnumSet.allOf(DrugClass.class).stream().map(DrugClass::name)
				.filter(d -> !d.startsWith("R")).toArray(String[]::new);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		TermServerReport.run(DrugsContentExplorer.class, args, new HashMap<>());
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1H7T_dqmvQ-zaaRtfrd-T3QCUMD7_K8st"; //Drugs Analysis
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		drugClassesOfInterest = getJob().getParameters().getValues(RESTRICT_TO_CLASSES);
		if (drugClassesOfInterest.isEmpty()) {
			drugClassesOfInterest = Arrays.asList(defaultDrugClasses);
		}

		allKnownDrugs = MEDICINAL_PRODUCT.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);


		String[] columnHeadings = new String[] {
				"SCTID, FSN, SemTag, Ingredient Count, Count of Base, Expression, Definition Status, Drug Class, MP Descendants, MPO Descendants, MPF Descendants, MPFO Descendants, RMPF Descedants, RMPFO Descendants, CD Descendants, RCD Descendants, Unknown Descendants, MP Ancestors, MPO Ancestors, MPF Ancestors, MPFO Ancestors, RMPF Ancestors, RMPFO Ancestors, CD Ancestors, RCD Ancestors, Unknown Ancestors "
				};
		String[] tabNames = new String[] {	
				"Drugs Content Explored"
				};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.STRING).withDefaultValue(true)
				.add(RESTRICT_TO_CLASSES).withType(JobParameter.Type.CHECKBOXES)
					.withValues(EnumSet.allOf(DrugClass.class).stream().map(DrugClass::name).toArray(String[]::new))
					.withDefaultValues(defaultDrugClasses)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Unique Attribute Pairs")
				.withDescription("This report lists combinations of dose forms and units along with usage counts and examples.  Also BoSS/PAI/MDF combinations.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		List<Concept> conceptsToReport;
		if (StringUtils.isEmpty(subsetECL)) {
			conceptsToReport = new ArrayList<>(allKnownDrugs);
		} else {
			conceptsToReport = new ArrayList<>(findConcepts(subsetECL));
		}

		LOGGER.info("Reporting on " + conceptsToReport.size() + " concepts");

		conceptsToReport.stream()
				.sorted(Comparator.comparing(Concept::getFsn))
				.peek(c -> DrugUtils.setConceptType(c))
				.filter(c -> hasDrugClassOfInterest(c))
				.forEach(c -> analyzeConcept(c));
	}

	private boolean hasDrugClassOfInterest(Concept c) {
		return drugClassesOfInterest.stream()
				.anyMatch(d -> traslateConceptTypeToString(c.getConceptType()).equals(d));
	}

	private String traslateConceptTypeToString(ConceptType conceptType) {
		if (conceptType == null) {
			return "UNKNOWN";
		}
		switch(conceptType) {
			case MEDICINAL_PRODUCT: return "MP";
			case MEDICINAL_PRODUCT_FORM: return "MPF";
			case MEDICINAL_PRODUCT_FORM_ONLY: return "MPFO";
			case MEDICINAL_PRODUCT_ONLY: return "MPO";
			//case REAL_MEDICINAL_PRODUCT_FORM: return "RMPF";
			//case REAL__MEDICINAL_PRODUCT_FORM_ONLY: return "RMPFO";
			case CLINICAL_DRUG: return "CD";
			//case REAL_CLINICAL_DRUG: return "RCD";
			case PRODUCT: return "PRODUCT";
			default: throw new IllegalArgumentException("Unexpected concept type: " + conceptType);
		}
	}

	private void analyzeConcept(Concept c) {
		Map<String, Integer> descendantData = new HashMap<>();
		Map<String, Integer> ancestorData = new HashMap<>();
		try {
			c.getDescendants(NOT_SET).stream()
					.peek(d -> DrugUtils.setConceptType(d))
					.map(d -> traslateConceptTypeToString(d.getConceptType()))
					.forEach(s -> descendantData.merge(s, 1, Math::addExact));
			c.getAncestors(NOT_SET).stream()
					.filter(a -> allKnownDrugs.contains(a))
					.peek(d -> DrugUtils.setConceptType(d))
					.map(d -> traslateConceptTypeToString(d.getConceptType()))
					.forEach(s -> ancestorData.merge(s, 1, Math::addExact));
			report(c,
					DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP).size(),
					DrugUtils.getCountOfBaseOrNA(c),
					c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP),
					SnomedUtils.translateDefnStatus(c.getDefinitionStatus()),
					traslateConceptTypeToString(c.getConceptType()),
					descendantData.getOrDefault("MP", 0),
					descendantData.getOrDefault("MPO", 0),
					descendantData.getOrDefault("MPF", 0),
					descendantData.getOrDefault("MPFO", 0),
					descendantData.getOrDefault("RMPF", 0),
					descendantData.getOrDefault("RMPFO", 0),
					descendantData.getOrDefault("CD", 0),
					descendantData.getOrDefault("RCD", 0),
					descendantData.getOrDefault("UNKNOWN", 0),
					ancestorData.getOrDefault("MP", 0),
					ancestorData.getOrDefault("MPO", 0),
					ancestorData.getOrDefault("MPF", 0),
					ancestorData.getOrDefault("MPFO", 0),
					ancestorData.getOrDefault("RMPF", 0),
					ancestorData.getOrDefault("RMPFO", 0),
					ancestorData.getOrDefault("CD", 0),
					ancestorData.getOrDefault("RCD", 0),
					ancestorData.getOrDefault("UNKNOWN", 0));
		} catch (TermServerScriptException e) {
			try {
				report(c, e.getMessage());
			} catch (TermServerScriptException ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
