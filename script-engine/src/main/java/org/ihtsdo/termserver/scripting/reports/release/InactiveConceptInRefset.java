package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-370 List concepts being inactivated in this release which 
 */
public class InactiveConceptInRefset extends TermServerReport implements ReportClass {
	
	private String browserUrl = "https://browser.ihtsdotools.org/snowstorm/snomed-ct";
	private String codeSystemName = "SNOMEDCT";
	private TermServerClient browser;
	private String browserPath;
	private List<Concept> referenceSets;
	private List<Concept> emptyReferenceSets;
	private AtomicLongMap<Concept> refsetSummary = AtomicLongMap.create();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(InactiveConceptInRefset.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		super.init(run);
		
		browser = new TermServerClient(browserUrl, authenticatedCookie);
		CodeSystem codeSystem = browser.getCodeSystem(codeSystemName);
		browserPath = codeSystem.getLatestVersion().getBranchPath();
		ConceptCollection refsetWrapper = browser.getConcepts("< 446609009 |Simple type reference set (foundation metadata concept)|",
				browserPath, null, TermServerClient.MAX_PAGE_SIZE);
		referenceSets = removeEmptyRefsets(refsetWrapper);
		debug ("Recovered " + referenceSets.size() + " simple reference sets");
	}


	private List<Concept> removeEmptyRefsets(ConceptCollection refsetWrapper) throws TermServerScriptException {
		List<Concept> populatedRefsets = new ArrayList<>();
		emptyReferenceSets = new ArrayList<>();
		for (Concept refset : refsetWrapper.getItems()) {
			if (browser.getConcepts("^" + refset, browserPath, null, 1).getTotal() > 0) {
				populatedRefsets.add(refset);
			} else {
				//Can't report on this until postInit complete
				emptyReferenceSets.add(refset);
			}
			try { Thread.sleep(1 * 1000); } catch (Exception e) {}
		}
		return populatedRefsets;
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Detail",
				"Id, FSN, SemTag, Detail"};
		String[] tabNames = new String[] {	
				"Summary",
				"Concepts"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Concepts in Refsets")
				.withDescription("This report lists concepts inactivated in the current authoring cycle which are members of a published International reference set.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//We're looking for concepts which are inactive and have no effective time
		//TODO Also allow running against published packages, at which point we'll be checking for a known effective time
		List<String> inactivatedConcepts = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive() && StringUtils.isEmpty(c.getEffectiveTime())) {
				inactivatedConcepts.add(c.getId());
			}
		}
		
		debug ("Checking " + inactivatedConcepts.size() + " inactivated concepts against " + referenceSets.size() + " refsets");
		
		//Now loop through all the referencesets and filter the inactivated concepts
		for (Concept refset : referenceSets) {
			Iterator<String> i = inactivatedConcepts.iterator();
			String eclPartial = "^" + refset.getId() + " AND (";
			String ecl = eclPartial;
			while (i.hasNext()) {
				ecl += i.next();
				if (i.hasNext() && ecl.length() < 2000) {
					ecl += " OR ";
				}
				
				if (ecl.length() > 2005) {
					recoverAndReport(refset, ecl);
					ecl = eclPartial;
				}
			}
			
			//Any final to finish off?
			if (ecl.length() > eclPartial.length()) {
				recoverAndReport(refset, ecl);
			}
		}
		
		//Output summary counts
		for (Map.Entry<Concept, Long> entry : refsetSummary.asMap().entrySet()) {
			report (PRIMARY_REPORT, entry.getKey(), entry.getValue());
		}
		
		
		for (Concept emptyRefset : emptyReferenceSets) {
			report(PRIMARY_REPORT, emptyRefset, " not populated at " + browserUrl);
		}
	}

	private void recoverAndReport(Concept refset, String ecl) throws TermServerScriptException {
		ecl += " )";
		incrementSummaryInformation("ECL queries excecuted");
		for (Concept c : browser.getConcepts(ecl, browserPath, null, TermServerClient.MAX_PAGE_SIZE).getItems()) {
			report (SECONDARY_REPORT, refset, c);
			refsetSummary.getAndIncrement(refset);
			countIssue(c);
		}
		try {
			Thread.sleep(1 * 1000);  //Trying to avoid rate limiting lockout
		} catch (Exception e) {}
		
	}

}