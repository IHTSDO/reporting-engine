package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-414
 */
public class NewDescriptions extends TermServerReport implements ReportClass {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NewDescriptions.class);
	private final Map<Annotation, Integer> annotationCount = new TreeMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, Boolean.FALSE.toString());
		params.put(ECL, "<< 404684003 |Clinical finding|");
		run(NewDescriptions.class, args, params);
	}
	
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		getArchiveManager().setRunIntegrityChecks(false);

		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, Description",
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, Text Definition",
				"ConceptId, FSN, SemTag, ReferencedComponentId, Lang, AnnotationId, AnnotationType, AnnotationValue, Annotation",
				"AnnotationType, AnnotationValue, Count"
		};
		String[] tabNames = new String[] {	
				"Description", "Text Definition", "Annotation", "Annotation Count"};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("New Descriptions")
				.withDescription("This report provides a list of all descriptions and annotations " +
						"created during the current authoring cycle, with an optional filter by ECL. " +
						"Checking 'Unpromoted Changes' shows only new descriptions and annotations " +
						"that were created since the project was last promoted.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		//Are we running locally from command line?
		if (jobRun.getTerminologyServerUrl() == null) {
			LOGGER.warn("No TS specified.  Using localhost");
			jobRun.setTerminologyServerUrl("http://localhost:8085/");
		}

		LOGGER.debug("Getting concepts of interest...");
		List<Concept> conceptsOfInterest = getConceptsOfInterest();

		LOGGER.debug("Running integrity checks...");
		checkRemoveMalformedConcepts(conceptsOfInterest);

		LOGGER.debug("Sorting concepts of interest...");
		conceptsOfInterest.sort(Comparator.comparing(Concept::getSemTag).thenComparing(Concept::getFsn));

		LOGGER.debug("Sorted. Processing...");
		for (Concept c : conceptsOfInterest) {
			processDescriptions(c);
			processAnnotations(c);
		}
		for (Map.Entry<Annotation, Integer> entry : annotationCount.entrySet()) {
			report(QUATERNARY_REPORT, entry.getKey().annotationType, entry.getKey().annotationValue, entry.getValue());
		}
	}

	private boolean unpromotedCheck(Component c) {
		//Are we filtering for unpromoted changes only?
		return !(unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c));
	}

	private List<Concept> getConceptsOfInterest() throws TermServerScriptException {
		List<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		} else {
			conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		}
		return conceptsOfInterest;
	}

	private void checkRemoveMalformedConcepts(List<Concept> conceptsOfInterest) throws TermServerScriptException{
		//DK Is throwing a wobbly here missing an FSN or SemTag.  Let's check them all.
		List<Concept> ignore = new ArrayList<>();
		for (Concept c : conceptsOfInterest) {
			//Ah, apparently DK really released a concept with no descriptions or relationships.
			//We'll complain but skip
			if (Boolean.TRUE.equals(c.isActive())) {
				if (c.getFsn() == null) {
					throw new TermServerScriptException("Integrity failure. " + c.getId() + " has no FSN");
				}
				if (c.getSemTag() == null) {
					throw new TermServerScriptException("Integrity failure. " + c.getId() + " has no Semantic Tag");
				}
			} else if (c.getFsn() == null || c.getSemTag() == null) {
				LOGGER.warn("Inactive concept {} has a missing or malformed FSN", c.getId());
				ignore.add(c);
			}
		}
		conceptsOfInterest.removeAll(ignore);
	}

	private void processDescriptions(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.isReleased() == null) {
				throw new TermServerScriptException("Released flag not populated. Code issues - should not use cached project snapshot when released flag is required");
			}
			if (Boolean.FALSE.equals(d.isReleased()) && inScope(d) && unpromotedCheck(d)) {
				int tabIdx = d.getType().equals(DescriptionType.TEXT_DEFINITION) ? SECONDARY_REPORT : PRIMARY_REPORT;
				report(tabIdx, c, d.getLang(), d.getId(), d.getTerm(), d);
				countIssue(c);
			}
		}
	}

	private void processAnnotations(Concept c) throws TermServerScriptException {
		for (Component comp : SnomedUtils.getAllComponents(c)) {
			for (ComponentAnnotationEntry a : comp.getComponentAnnotationEntries()) {
				if (Boolean.FALSE.equals(a.isReleased()) && inScope(a) && unpromotedCheck(a)) {
					Concept annotationType = gl.getConcept(a.getTypeId());
					report(TERTIARY_REPORT, c, comp.getId(), a.getLanguageDialectCode(), a.getId(), annotationType.getFsn(), a.getValue(), a);
					annotationCount.merge(new Annotation(annotationType.getFsn(), a.getValue()), 1, Integer::sum);
					countIssue(c);
				}
			}
		}
	}

	static class Annotation implements Comparable<Annotation> {
		String annotationType;
		String annotationValue;

		Annotation(String annotationType, String annotationValue) {
			this.annotationType = annotationType;
			this.annotationValue = annotationValue;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) return true;
			if (object == null || getClass() != object.getClass()) return false;
			Annotation otherAnnotation = (Annotation) object;
			return Objects.equals(annotationType, otherAnnotation.annotationType) && Objects.equals(annotationValue, otherAnnotation.annotationValue);
		}

		@Override
		public int hashCode() {
			return Objects.hash(annotationType, annotationValue);
		}

		@Override
		public int compareTo(@NotNull Annotation otherAnnotation) {
			if (this.annotationType.equals(otherAnnotation.annotationType)) {
				return this.annotationValue.compareTo(otherAnnotation.annotationValue);
			} else {
				return this.annotationType.compareTo(otherAnnotation.annotationType);
			}
		}
	}
}


