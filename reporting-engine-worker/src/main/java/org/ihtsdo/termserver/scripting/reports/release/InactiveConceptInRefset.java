package org.ihtsdo.termserver.scripting.reports.release;

import java.io.File;
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
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.springframework.util.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-370 List concepts being inactivated in this release which also appear in known refsets
 */
public class InactiveConceptInRefset extends TermServerReport implements ReportClass {
	
	static public String REFSET_ECL = "(< 446609009 |Simple type reference set| OR < 900000000000496009 |Simple map type reference set|) MINUS 900000000000497000 |CTV3 simple map reference set (foundation metadata concept)|";
	private Collection<Concept> referenceSets;
	private List<Concept> emptyReferenceSets;
	private List<Concept> outOfScopeReferenceSets;
	private AtomicLongMap<Concept> refsetSummary = AtomicLongMap.create();
	private AtomicLongMap<Concept> moduleSummary = AtomicLongMap.create();
	private static String INCLUDE_LAST_RELEASE = "Include latest Int release";
	private String lastReleaseEffectiveTime = null;
	private static String EXT_REF_ONLY = "Extension Refsets Only";
	private boolean extensionRefsetOnly = false;
	public static final int CLAUSE_LIMIT = 100;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_LAST_RELEASE, "true");
		params.put(EXT_REF_ONLY, "false");
		TermServerReport.run(InactiveConceptInRefset.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		if (getJobRun().getParameters().getMandatoryBoolean(INCLUDE_LAST_RELEASE)) {
			lastReleaseEffectiveTime = project.getMetadata().getDependencyRelease();
		}
		extensionRefsetOnly = getJobRun().getParameters().getMandatoryBoolean(EXT_REF_ONLY);
		
		referenceSets = findConcepts(REFSET_ECL);
		removeEmptyAndNoScopeRefsets();
		info ("Recovered " + referenceSets.size() + " simple reference sets and maps");

		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Count",
				"Id, FSN, SemTag, Count, Refset Module",
				"Id, FSN, SemTag, Refset, Concept Module, Refset Module"};
		String[] tabNames = new String[] {
				"Module Summary",
				"Per Refset Counts",
				"Concepts Inactivated in Refsets"};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void removeEmptyAndNoScopeRefsets() throws TermServerScriptException {
		emptyReferenceSets = new ArrayList<>();
		outOfScopeReferenceSets = new ArrayList<>();
		for (Concept refset : referenceSets) {
			if (extensionRefsetOnly && !inScope(refset)) {
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
		referenceSets.removeAll(outOfScopeReferenceSets);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_LAST_RELEASE).withType(Type.BOOLEAN).withMandatory().withDefaultValue("false")
				.add(EXT_REF_ONLY).withType(Type.BOOLEAN).withMandatory().withDefaultValue("false")
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Concepts in Refsets")
				.withDescription("This report lists concepts inactivated in the current authoring cycle" + 
				" which are members of a published reference set, or in a list of high usage concepts." +
				" Warning: because this report involves inactive concepts, it cannot use ECL and therefore takes ~40 minutes to run.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withExpectedDuration(40)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//If we are including the last release then all concepts are in scope and
		//inactivations from the previous international release are included
		List<Concept> inactivatedConcepts;
		if (lastReleaseEffectiveTime == null) {
			//We're looking for concepts which are inactive and have no effective time
			inactivatedConcepts = gl.getAllConcepts().stream()
					.filter(c -> !c.isActive())
					.filter(c -> inScope(c))
					.filter(c -> StringUtils.isEmpty(c.getEffectiveTime()))
					.collect(Collectors.toList());
		} else {
			inactivatedConcepts = gl.getAllConcepts().stream()
					.filter(c -> !c.isActive())
					.filter(c -> (StringUtils.isEmpty(c.getEffectiveTime()) || c.getEffectiveTime().equals(lastReleaseEffectiveTime)))
					.collect(Collectors.toList());
		}
		
		if (!extensionRefsetOnly) {
			debug ("Checking " + inactivatedConcepts.size() + " inactivated concepts against High Usage SCTIDs");
			List<String> inactivatedConceptIds = inactivatedConcepts.stream().
					map(c -> c.getId())
					.collect(Collectors.toList());
			checkHighVolumeUsage(inactivatedConceptIds);
		}
		
		debug ("Checking " + inactivatedConcepts.size() + " inactivated concepts against " + referenceSets.size() + " refsets");
		String viableRefsetECL = referenceSets.stream()
				.map(r -> r.getId())
				.collect(Collectors.joining(" OR "));
		
		int count = 0;
		for (List<Concept> inactiveConceptsSegment : Iterables.partition(inactivatedConcepts, CLAUSE_LIMIT)) {
			Collection<RefsetMember> members = findRefsetMembers(inactiveConceptsSegment, viableRefsetECL);
			for (RefsetMember m : members) {
				Concept refset = gl.getConcept(m.getRefsetId());
				Concept module = gl.getConcept(refset.getModuleId());
				Concept c = gl.getConcept(m.getReferencedComponentId());
				
				//Ensure we initialise the module summary to capture 0 counts
				if (!moduleSummary.containsKey(module)) {
					moduleSummary.put(module, 0);
				}
				
				if (referenceSets.contains(refset)) {
					report (TERTIARY_REPORT, c, refset, c.getModuleId(), refset.getModuleId());
					refsetSummary.getAndIncrement(refset);
					moduleSummary.getAndIncrement(module);
					countIssue(c);
				}
			}
			try {
				Thread.sleep(1 * 200);
			} catch (Exception e) {}
			
			count += inactiveConceptsSegment.size();
			debug ("Checked " + count + " inactive concepts");
		}
		
		//Output summary counts
		for (Map.Entry<Concept, Long> entry : moduleSummary.asMap().entrySet()) {
			Concept module = entry.getKey();
			report(PRIMARY_REPORT, module, entry.getValue());
		}
		
		//Output summary counts
		for (Map.Entry<Concept, Long> entry : refsetSummary.asMap().entrySet()) {
			Concept refset = entry.getKey();
			report(SECONDARY_REPORT, refset, entry.getValue(), refset.getModuleId());
		}
		
		for (Concept emptyRefset : emptyReferenceSets) {
			report(SECONDARY_REPORT, emptyRefset, " not populated in project: " + getProject().getKey(), emptyRefset.getModuleId());
		}
		
		for (Concept outOfScopeReferenceSet : outOfScopeReferenceSets) {
			report(SECONDARY_REPORT, outOfScopeReferenceSet, " out of scope in project: " + getProject().getKey(), outOfScopeReferenceSet.getModuleId());
		}
	}
	
	private void checkHighVolumeUsage(List<String> inactivatedIds) throws TermServerScriptException {
		String fileName = "resources/HighVolumeSCTIDs.txt";
		Concept hvu = new Concept("0","High Volume Usage (UK)");
		hvu.setModuleId(SCTID_CORE_MODULE);
		debug ("Loading " + fileName );
		try {
			List<String> lines = Files.readLines(new File(fileName), Charsets.UTF_8);
			for (String line : lines) {
				String id = line.split(TAB)[0];
				if (inactivatedIds.contains(id)) {
					Concept concept = gl.getConcept(id);
					report (TERTIARY_REPORT, concept, "High Volume Usage", concept.getModuleId(), "N/A");
					refsetSummary.getAndIncrement(hvu);
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
	}

}