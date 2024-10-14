package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.HashMap;
import java.util.Map;

public class DetermineSequenceNumbers extends TermServerReport implements ReportClass {

	Map<String, Long> partitionSequenceNumbers = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(DetermineSequenceNumbers.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		additionalReportColumns = "Partition, Maximum Sequence Number";
		allowMissingExpectedModules = true;
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Find new clones")
				.withDescription("List all concepts with one semantic tag that have lexical equivalents in another tag, optionally ignoring some text")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			for (Component comp : SnomedUtils.getAllComponents(c)) {
				if (inScope(comp)) {
					checkSequenceNumber(comp);
				}
			}
		}

		for (Map.Entry<String, Long> entry : partitionSequenceNumbers.entrySet()) {
			report(PRIMARY_REPORT, entry.getKey(), entry.getValue());
		}
	}

	private void checkSequenceNumber(Component comp) {
		//Don't worry about refset members, they have UUIDs
		if (comp instanceof RefsetMember) {
			return;
		}
		String partition = SnomedUtils.getPartition(comp.getId());
		Long sequenceNumber = SnomedUtils.getSequenceNumber(comp.getId());
		// If we've not seen this partition before, or this sequence number is higher, remember it
		partitionSequenceNumbers.computeIfAbsent(partition, k -> sequenceNumber);
		if (partitionSequenceNumbers.get(partition) < sequenceNumber) {
		    partitionSequenceNumbers.put(partition, sequenceNumber);
		}
	}


}
