package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
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
	
	static public String REFSET_ECL = "(< 446609009 |Simple type reference set| OR < 900000000000496009 |Simple map type reference set|) MINUS 900000000000497000 |CTV3 simple map reference set (foundation metadata concept)|";
	private Collection<Concept> referenceSets;
	private List<Concept> emptyReferenceSets;
	private List<Concept> outOfScopeReferenceSets;
	private AtomicLongMap<Concept> refsetSummary = AtomicLongMap.create();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(InactiveConceptInRefset.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		referenceSets = findConcepts(REFSET_ECL);
		removeEmptyAndNoScopeRefsets();
		info ("Recovered " + referenceSets.size() + " simple reference sets and maps");

		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Detail",
				"Id, FSN, SemTag, Detail"};
		String[] tabNames = new String[] {	
				"Summary",
				"Concepts Inactivated in Refsets"};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void removeEmptyAndNoScopeRefsets() throws TermServerScriptException {
		emptyReferenceSets = new ArrayList<>();
		outOfScopeReferenceSets = new ArrayList<>();
		for (Concept refset : referenceSets) {
			if (!inScope(refset)) {
				outOfScopeReferenceSets.add(refset);
				continue;
			}
			if (getConceptsCount("^" + refset) == 0) {
				emptyReferenceSets.add(refset);
			} else {
				refsetSummary.put(refset, 0);
			}
			try { Thread.sleep(1 * 1000); } catch (Exception e) {}
		}
		referenceSets.removeAll(emptyReferenceSets);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Concepts in Refsets")
				.withDescription("This report lists concepts inactivated in the current authoring cycle" + 
				" which are members of a published reference set." +
				" Warning: because this report involves inactive concepts, it cannot use ECL and therefore takes ~30 minutes to run.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//We're looking for concepts which are inactive and have no effective time
		//TODO Also allow running against published packages, at which point we'll be checking for a known effective time
		List<Concept> inactivatedConcepts = gl.getAllConcepts().stream()
				.filter(c -> !c.isActive())
				.filter(c -> inScope(c))
				.filter(c -> StringUtils.isEmpty(c.getEffectiveTime()))
				.collect(Collectors.toList());
		debug ("Checking " + inactivatedConcepts.size() + " inactivated concepts against " + referenceSets.size() + " refsets");
		int count = 0;
		for (Concept c : inactivatedConcepts) {
			if (++count % 100 == 0) {
				debug ("Checked " + count + " inactive concepts");
			}
			Collection<RefsetMember> members = findRefsetMembers(c, REFSET_ECL);
			for (RefsetMember m : members) {
				Concept refset = gl.getConcept(m.getRefsetId());
				if (referenceSets.contains(refset)) {
					report (SECONDARY_REPORT, c, refset);
					refsetSummary.getAndIncrement(refset);
					countIssue(c);
				}
			}
			try {
				Thread.sleep(1 * 200);
			} catch (Exception e) {}
		}
		
		//Output summary counts
		for (Map.Entry<Concept, Long> entry : refsetSummary.asMap().entrySet()) {
			report(PRIMARY_REPORT, entry.getKey(), entry.getValue());
		}
		
		for (Concept emptyRefset : emptyReferenceSets) {
			report(PRIMARY_REPORT, emptyRefset, " not populated in project: " + getProject().getKey());
		}
		
		for (Concept outOfScopeReferenceSet : outOfScopeReferenceSets) {
			report(PRIMARY_REPORT, outOfScopeReferenceSet, " out of scope in project: " + getProject().getKey());
		}
	}

}