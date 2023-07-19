package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FsnPtAlignmentStats extends TermServerReport implements ReportClass {

	private static Logger LOGGER = LoggerFactory.getLogger(FsnPtAlignmentStats.class);

	DecimalFormat df = new DecimalFormat("##.#%");
	boolean includeDetail = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<<372087000 |Primary malignant neoplasm (disorder)|");
		TermServerReport.run(FsnPtAlignmentStats.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		headers = "Hierarchy, FSN PT Aligns, FSN PT Misaligned, US/GB Variance, US Align, GB Align";
		additionalReportColumns = "";
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, SemTag, Hierarchy, FSN PT Aligns, FSN PT Misaligned, US/GB Variance, US Align, GB Align",
				"SCTID, FSN, SemTag, FSN, PT",};
		String[] tabNames = new String[] {	"Summary",
				"Details"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.STRING)
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
		if (StringUtils.isEmpty(subsetECL)) {
			List<Concept> hierarchies = SnomedUtils.sort(ROOT_CONCEPT.getChildren(CharacteristicType.STATED_RELATIONSHIP));
			for (Concept hierarchy : hierarchies) {
				Collection<Concept> concepts = hierarchy.getDescendents(NOT_SET);
				reportAlignmentStats(hierarchy.toStringPref(), concepts);
			}
		} else {
			includeDetail = true;
			List<Concept> concepts = SnomedUtils.sort(findConcepts(subsetECL));
			reportAlignmentStats("ECL Selection", concepts);
		}
	}

	private void reportAlignmentStats(String localArea, Collection<Concept> concepts) throws TermServerScriptException {
		int align = 0, misalign = 0, variance = 0, usAlign = 0, gbAlign = 0;
		for (Concept c : concepts) {
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
				if (includeDetail) {
					report(SECONDARY_REPORT, c, usPt);
				}
			}
		}
		int total = align + misalign;
		String alignStr = align + " (" + df.format(align/(double)total) + ")";
		String misalignStr = misalign + " (" + df.format(misalign/(double)total) + ")";
		String varianceStr = variance + " (" + df.format(variance/(double)total) + ")";
		String usStr = usAlign + " (" + df.format(usAlign/(double)variance) + ")";
		String gbStr = gbAlign + " (" + df.format(gbAlign/(double)variance) + ")";
		report (PRIMARY_REPORT, localArea, alignStr, misalignStr, varianceStr, usStr, gbStr );
	}


}
