package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-542 Get stats broken down by major hierarchy on what FSNs align with their PTs
 */
public class FsnPtAlignmentStats extends TermServerReport implements ReportClass {
	
	DecimalFormat df = new DecimalFormat("##.#%");
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(FsnPtAlignmentStats.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		headers = "Hierarchy, FSN PT Aligns, FSN PT Misaligned, US/GB Variance, US Align, GB Align";
		additionalReportColumns = "";
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(SUBSTANCE)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("FSN / PT Alignment Stats")
				.withDescription("This report gives counts of how many preferred terms align with the FSN (ie are the FSN without the semantic tag)")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		List<Concept> hierarchies = SnomedUtils.sort(ROOT_CONCEPT.getChildren(CharacteristicType.STATED_RELATIONSHIP));
		for (Concept hierarchy : hierarchies) {
			int align = 0, misalign = 0, variance = 0, usAlign = 0, gbAlign = 0;
			for (Concept c : hierarchy.getDescendents(NOT_SET)) {
				String fsnMinusSemtag = SnomedUtils.deconstructFSN(c.getFsn())[0];
				String gbPt = c.getPreferredSynonym(GB_ENG_LANG_REFSET).getTerm();
				String usPt = c.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
				if (!gbPt.equals(usPt)) {
					variance++;
					if (gbPt.equals(fsnMinusSemtag)) {
						align++;
						gbAlign++;
					} else if (usPt.equals(fsnMinusSemtag)) {
						align++;
						usAlign++;
					} else {
						misalign++;
					}
				} else if (usPt.equals(fsnMinusSemtag)) {
					align++;
				} else {
					misalign++;
				}
			}
			int total = align + misalign;
			String alignStr = align + " (" + df.format(align/(double)total) + ")";
			String misalignStr = misalign + " (" + df.format(misalign/(double)total) + ")";
			String varianceStr = variance + " (" + df.format(variance/(double)total) + ")";
			String usStr = usAlign + " (" + df.format(usAlign/(double)variance) + ")";
			String gbStr = gbAlign + " (" + df.format(gbAlign/(double)variance) + ")";
			report (PRIMARY_REPORT, hierarchy.toStringPref(), alignStr, misalignStr, varianceStr, usStr, gbStr );
		}
	}


}
