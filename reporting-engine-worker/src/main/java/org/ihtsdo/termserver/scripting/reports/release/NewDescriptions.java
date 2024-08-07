package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
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
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, Boolean.FALSE.toString());
		//params.put(UNPROMOTED_CHANGES_ONLY, Boolean.TRUE.toString());
		params.put(ECL, "<< 60700002");
		TermServerReport.run(NewDescriptions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);

		getArchiveManager().setRunIntegrityChecks(false);

		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, Description",
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, Text Definition",
				"ConceptId, FSN, SemTag, ReferencedComponentId, Lang, AnnotationId, AnnotationType, AnnotationValue, Annotation"
		};
		String[] tabNames = new String[] {	
				"Description", "Text Definition", "Annotation"};
		super.postInit(tabNames, columnHeadings, false);
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
				.withDescription("This report lists all descriptions and annotations (optionally filtered by ECL) created in the current authoring cycle. " +
				"Ticking the 'Unpromoted Changes' box will cause only those new descriptions and annotations that have been created since the last time the project was promoted, to be listed.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//Are we running locally from command line?
		if (jobRun.getTerminologyServerUrl() == null) {
			LOGGER.warn("No TS specified.  Using localhost");
			jobRun.setTerminologyServerUrl("http://localhost:8085/");
		}
		
		List<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		} else {
			conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		}
		
		//DK Is throwing a wobbly here missing an FSN or SemTag.  Let's check them all.
		List<Concept> ignore = new ArrayList<>();
		for (Concept c : conceptsOfInterest) {
			//Ah, apparently DK really released a concept with no descriptions or relationships.
			//We'll complain but skip
			if (c.isActive()) {
				if (c.getFsn() == null) {
					throw new TermServerScriptException ("Integrity failure. " + c.getId() + " has no FSN");
				}
				if (c.getSemTag() == null) {
					throw new TermServerScriptException ("Integrity failure. " + c.getId() + " has no Semantic Tag");
				}
			} else if (c.getFsn() == null || c.getSemTag() == null){
				LOGGER.warn ("Inactive concept " + c.getId() + " has a missing or malformed FSN");
				ignore.add(c);
			}
		}
		conceptsOfInterest.removeAll(ignore);
		LOGGER.debug("Sorting...");
		conceptsOfInterest.sort(Comparator.comparing(Concept::getSemTag).thenComparing(Concept::getFsn));
		LOGGER.debug("Sorted.");
		
		for (Concept c : conceptsOfInterest) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.isReleased() == null) {
					throw new TermServerScriptException("Released flag not populated. Code issues - should not use cached project snapshot when released flag is required");
				}
				if (!d.isReleased() && inScope(d) && unpromotedCheck(d)) {
					int tabIdx = d.getType().equals(DescriptionType.TEXT_DEFINITION) ? SECONDARY_REPORT : PRIMARY_REPORT;
					report(tabIdx, c, d.getLang(), d.getId(), d.getTerm(), d);
					countIssue(c);
				}
			}
			for (Component comp : SnomedUtils.getAllComponents(c)) {
				for (ComponentAnnotationEntry a : comp.getComponentAnnotationEntries()) {
					if (!a.isReleased() && inScope(a) && unpromotedCheck(a)) {
						Concept annotationType = gl.getConcept(a.getTypeId());
						report(TERTIARY_REPORT, c, comp.getId(), a.getLanguageDialectCode(), a.getId(), annotationType.getFsn(), a.getValue(), a);
						countIssue(c);
					}
				}
			}
		}
	}

	private boolean unpromotedCheck(Component c) {
		//Are we filtering for unpromoted changes only?
		return !(unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c));
	}

}
