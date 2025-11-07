package org.ihtsdo.termserver.scripting.reports.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class HistoricalAssociationsCrossingMajorHierarchies extends TermServerReport implements ReportClass {

	private static Logger LOGGER = LoggerFactory.getLogger(HistoricalAssociationsCrossingMajorHierarchies.class);
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(HistoricalAssociationsCrossingMajorHierarchies.class, args, params);
	}
	
	public static Multimap<String,String> allowedSemtagTransitions = ArrayListMultimap.create();
	static {
		allowedSemtagTransitions.put("(morphologic abnormality)", "(disorder)");
		allowedSemtagTransitions.put("(finding)", "(disorder)");
		allowedSemtagTransitions.put("(finding)", "(observable entity)");
		allowedSemtagTransitions.put("(finding)", "(event)");
		allowedSemtagTransitions.put("(finding)", "(qualifier value)");
		allowedSemtagTransitions.put("(product)", "(medicinal product)");
		allowedSemtagTransitions.put("(product)", "(medicinal product form)");
		allowedSemtagTransitions.put("(product)", "(clinical drug)");
		allowedSemtagTransitions.put("(body structure)", "(cell structure)");
		allowedSemtagTransitions.put("(body structure)", "(morphologic abnormality)");
		allowedSemtagTransitions.put("(medicinal product form)", "(clinical drug)");
		allowedSemtagTransitions.put("(situation)", "(finding)");
		allowedSemtagTransitions.put("(attribute)", "(qualifier value)");
		allowedSemtagTransitions.put("(assessment scale)", "(observable entity)");
		allowedSemtagTransitions.put("(event)", "(situation)");
		allowedSemtagTransitions.put("(environment)", "(finding)");
		allowedSemtagTransitions.put("(procedure)", "(regime/therapy)");
		allowedSemtagTransitions.put("(tumor staging)", "(qualifier value)");
	}
	
	public static Set<String> ignoreSemTags = new HashSet<>(); 
	static {
		ignoreSemTags.add("(namespace concept)");
		ignoreSemTags.add("(navigational concept)");
	}
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
		headers="SCTID, FSN, Semtag, Historical Association";
		additionalReportColumns="";
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concepts with Single Inferred Parent")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		int conceptsChecked = 0;
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> !c.isActive())
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
		
		nextConcept:
		for (Concept c : conceptsOfInterest) {
		//for (Concept c : Collections.singletonList(gl.getConcept("269636003"))) {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			//Only interested in cases where the semantic tag is still active
			if (!SnomedUtils.isActiveSemanticTag(semTag, gl) || ignoreSemTags.contains(semTag)) {
				continue;
			}
			conceptsChecked++;
			//Check all historical associations
			for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE)) {
				Concept target = gl.getConcept(a.getTargetComponentId());
				String targetSemTag = SnomedUtils.deconstructFSN(target.getFsn())[1];
				
				if (ignoreSemTags.contains(targetSemTag)) {
					continue nextConcept;
				}
				if (!semTag.equals(targetSemTag)) {
					//Is this an allowed transition?
					if (!allowedSemtagTransitions.containsEntry(semTag, targetSemTag) &&
							!allowedSemtagTransitions.containsEntry(targetSemTag, semTag)) {
						report(c, SnomedUtils.prettyPrintHistoricalAssociations(c, gl));
						countIssue(c);
						continue nextConcept;
					}
				}
			}
		}
		LOGGER.info("Checked " + conceptsChecked + " concepts");
	}
}
