package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-166 List all new concepts
 * RP-386 Update to include all new components
 * RP-387 Update to include new Language Refsets
 */
public class NewComponents extends TermServerReport implements ReportClass {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	AtomicLongMap<String> componentCounts = AtomicLongMap.create();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(NewComponents.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		subsetECL = run.getParamValue(ECL);
		getArchiveManager().setPopulateReleasedFlag(true);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Component Type, New Count",
				"Id, FSN, SemTag, Detail, Author, Task, Creation Date",
				"Id, FSN, SemTag, Detail, Detail",
				"Id, FSN, SemTag, Detail",
				"Id, FSN, SemTag, Detail",
				"Id, FSN, SemTag, Detail",
				"Id, FSN, SemTag, Detail",
				"Id, FSN, SemTag, Detail",
		};
		String[] tabNames = new String[] {
				"Summary",
				"Concepts",
				"Description",
				"Relationships",
				"Axioms",
				"Associations",
				"Inactivations",
				"Language Refsets",
		};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("New Components")
				.withDescription("This report lists all components (optionally filtered by ECL) created in the current authoring cycle")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//Are we running locally from command line?
		if (jobRun.getTerminologyServerUrl() == null) {
			logger.warn("No TS specified.  Using localhost");
			jobRun.setTerminologyServerUrl("http://localhost:8085/");
		}
		//SnowOwl and Snowstorm used different case significance for "Creating concept", historically
		TraceabilityService service = new TraceabilityService(jobRun, this, "reating concept");
		
		Collection<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept c : conceptsOfInterest) {
			if (!c.isReleased() && inScope(c)) {
				service.populateTraceabilityAndReport(SECONDARY_REPORT, c, (Object[])null);
				componentCounts.getAndIncrement("Concepts");
				countIssue(c);
			}
			
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (inScope(d)) {
					if (!d.isReleased()) {
						report(TERTIARY_REPORT, c, d.getTerm(), d);
						componentCounts.getAndIncrement("Descriptions");
						countIssue(c);
					}

					for (LangRefsetEntry langRefsetEntry : d.getLangRefsetEntries()) {
						if (inScope(langRefsetEntry) && !langRefsetEntry.isReleased()) {
							report(OCTONARY_REPORT, c, langRefsetEntry);
							componentCounts.getAndIncrement("Language Refsets");
							countIssue(c);
						}
					}
				}
			}
			
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!r.isReleased() && inScope(r)) {
					report(QUATERNARY_REPORT, c, r);
					componentCounts.getAndIncrement("Relationships");
					countIssue(c);
				}
			}
			
			for (AxiomEntry a : c.getAxiomEntries(ActiveState.ACTIVE, true)) {
				if (!a.isReleased() && inScope(a)) {
					report(QUINARY_REPORT, c, a);
					componentCounts.getAndIncrement("Axioms");
					countIssue(c);
				}
			}
			
			for (AssociationEntry a : c.getAssociationEntries()) {
				if (!a.isReleased() && inScope(a)) {
					report(SENARY_REPORT, c, a);
					componentCounts.getAndIncrement("Concept Associations");
					countIssue(c);
				}
			}
			
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
				if (!i.isReleased() && inScope(i)) {
					report(SEPTENARY_REPORT, c, i);
					componentCounts.getAndIncrement("Concept Inactivation Indicators");
					countIssue(c);
				}
			}
		}
		
		for (Map.Entry<String, Long> entry : componentCounts.asMap().entrySet()) {
			report (PRIMARY_REPORT, entry.getKey(), entry.getValue());
		}
		//Finish off adding traceability and reporting out any remaining concepts that 
		//haven't filed a batch
		service.flush();
	}

}