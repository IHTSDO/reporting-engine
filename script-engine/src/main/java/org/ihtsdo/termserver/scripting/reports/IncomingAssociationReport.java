package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * QI-300 List concepts selected by ECL, filtered lexically 
 * which have an incoming historical association pointing to them.
 */
public class IncomingAssociationReport extends TermServerScript implements ReportClass {
	
	public static String TEXT_MATCH = "Text Match";
	List<String> textMatches;
	Set<Concept> exclusions = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<<" + CLINICAL_FINDING + " OR <<" + SITN_WITH_EXP_CONTXT );
		params.put(TEXT_MATCH,"on examination,o/e,complaining of,c/o");
		TermServerReport.run(IncomingAssociationReport.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL).withMandatory()
				.add(TEXT_MATCH).withType(Type.STRING)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.GENERAL_QA))
				.withName("Incoming Historical Associations")
				.withDescription("List concepts matching both ECL and lexically, which have an incoming historical assertion. Multiple terms can be specified, separated with a comma.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1PWtDYFfqLoUwk17HlFgNK648Mmra-1GA"; //General QA
		textMatches = Arrays.asList(run.getParamValue(TEXT_MATCH, "").toLowerCase().split(COMMA));
		textMatches.replaceAll(String::trim);
		subsetECL = run.getMandatoryParamValue(ECL);
		super.init(run);
		additionalReportColumns = "FSN (Inactive Concept), SemTag, EffectiveTime, Assoc Type, Target (Active Concept), Details";
	}
	
	public void postInit() throws TermServerScriptException {
		if (inputFile != null) {
			loadExclusions();
		}
		super.postInit();
	}

	public void runJob() throws TermServerScriptException {
		info ("Scanning concepts...");
		for (Concept c : findConcepts(subsetECL)) {
			if (inScope(c)) {
				for (AssociationEntry entry : gl.usedAsHistoricalAssociationTarget(c)) {
					Concept source = gl.getConcept(entry.getReferencedComponentId());
					String assocType = SnomedUtils.getAssociationType(entry);
					report (source, source.getEffectiveTime(), assocType, c, entry);
					countIssue(c);
				}
			}
		}
	}
	
	public boolean inScope (Concept c) {
		String fsn = c.getFsn().toLowerCase();
		for (String textMatch : textMatches) {
			if (fsn.contains(textMatch)) {
				return true;
			}
		}
		return false;
	}
	

	private void loadExclusions() throws TermServerScriptException {
		if (!inputFile.canRead()) {
			throw new TermServerScriptException ("Cannot read: " + inputFile);
		}
		try {
			List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
			for (String line : lines) {
				exclusions.add(gl.getConcept(line.trim(), false, true));
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to load " + inputFile, e);
		}
	}
}
