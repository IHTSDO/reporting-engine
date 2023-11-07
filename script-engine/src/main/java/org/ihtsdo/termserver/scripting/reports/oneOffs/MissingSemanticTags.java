package org.ihtsdo.termserver.scripting.reports.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;


/**
 * RP-605 List any concepts (which will almost certainly be inactive) where the FSN
 * does not contain a currently valid semantic tag.
 */
public class MissingSemanticTags extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, BODY_STRUCTURE.toString());
		TermServerReport.run(MissingSemanticTags.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
		headers="SCTID, FSN, Found, EffectiveTime, Issue, Last Known Position, Historical Relationships,";
		additionalReportColumns="";
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List Missing Semantic Tags")
				.withDescription("This report concepts (which will almost certainly be inactive) where the FSN " + 
						" does not contain a currently valid semantic tag")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		Set<String> validSemTags = new HashSet<>();
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (Concept topLevel : ROOT_CONCEPT.getDescendants(IMMEDIATE_CHILD)) {
			Set<Concept> descendants = topLevel.getDescendants(NOT_SET);
			for (Concept thisDescendent : descendants) {
				validSemTags.add(SnomedUtils.deconstructFSN(thisDescendent.getFsn())[1]);
			}
		}
		
		//Now work through all Concepts and list any that don't have an active semantic tag
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			Description fsn = c.getFSNDescription();
			if (fsn == null) {
				report(c, c.getEffectiveTime(), "No FSN detected");
				continue;
			}
			String semTag = SnomedUtils.deconstructFSN(fsn.getTerm())[1];
			if (StringUtils.isEmpty(semTag)) {
				report(c, c.getEffectiveTime(),  "No brackets detected");
				continue;
			}
			if (!validSemTags.contains(semTag)) {
				String isA = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_A, ActiveState.BOTH)
						.stream()
						.map(r -> r.toString())
						.collect(Collectors.joining(",\n"));
				String histAssocs = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
				report(c, c.getEffectiveTime(), "Invalid Semtag", isA, histAssocs);
			}
		}
	}
}
