package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.io.Files;

/**
 * QI-300 List concepts selected by ECL, filtered lexically 
 * which have an incoming historical association pointing to them.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncomingAssociationReport extends TermServerReport implements ReportClass {

	static {
		ReportSheetManager.targetFolderId = "1ndqzuQs7C-8ODbARPWh4xJVshWIDF9gN"; //QI
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(IncomingAssociationReport.class);

	public static final String TEXT_MATCH = "Text Match";
	List<String> textMatches;
	Set<Concept> exclusions = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 23406007 |Fracture of upper limb|");
		TermServerScript.run(IncomingAssociationReport.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL).withMandatory()
				.add(TEXT_MATCH).withType(Type.STRING)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Incoming Historical Associations")
				.withDescription("List concepts matching both ECL and lexically, which have an incoming historical assertion. Multiple terms can be specified, separated with a comma.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		textMatches = Arrays.asList(run.getParamValue(TEXT_MATCH, "").toLowerCase().split(COMMA));
		textMatches.replaceAll(String::trim);
		subsetECL = run.getMandatoryParamValue(ECL);
		super.init(run);
		additionalReportColumns = "FSN (Inactive Concept), SemTag, EffectiveTime, Assoc Type, Target (Active Concept), Details";
	}

	@Override
	public void postInit() throws TermServerScriptException {
		if (hasInputFile()) {
			loadExclusions();
		}
		super.postInit();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		LOGGER.info ("Scanning concepts...");
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
		if (textMatches == null || textMatches.isEmpty()) {
			return true;
		}

		String fsn = c.getFsn().toLowerCase();
		for (String textMatch : textMatches) {
			if (fsn.contains(textMatch)) {
				return true;
			}
		}
		return false;
	}
	

	private void loadExclusions() throws TermServerScriptException {
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException ("Cannot read: " + getInputFile());
		}
		try {
			List<String> lines = Files.readLines(getInputFile(), StandardCharsets.UTF_8);
			for (String line : lines) {
				exclusions.add(gl.getConcept(line.trim(), false, true));
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to load " + getInputFile(), e);
		}
	}
}
