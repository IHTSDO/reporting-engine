package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

/**
 * QI-135 Report to find concepts newly created (null effective time) with one 
 * semantic tag that have
 * Note that because we also work with inactive concepts, no subhierarchy is specified
 */
public class FindNewClones extends TermServerReport implements ReportClass {

	public static final String TARGET_SEMTAG = "Target SemTag";
	public static final String SOURCE_SEMTAG = "Source SemTag";
	public static final String IGNORE_TEXT = "Ignore Text";
	public static final String ATTRIBUTE_ECL = "Attribute ECL";
	Map<String, List<Concept>> sourceMap = new HashMap<>();
	Map<String, Concept> targetMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, CLINICAL_FINDING.getConceptId());
		params.put(TARGET_SEMTAG, "(finding)");
		params.put(SOURCE_SEMTAG, "(disorder)");
		params.put(IGNORE_TEXT, "skin of ,of ");
		params.put(ATTRIBUTE_ECL, "116676008 |Associated morphology (attribute)| = 400061001 |Abrasion (morphologic abnormality)| ");
		TermServerScript.run(FindNewClones.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Matched, Source Active";
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { TARGET_SEMTAG, SOURCE_SEMTAG, IGNORE_TEXT};
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Find new clones")
				.withDescription("List all concepts with one semantic tag that have lexical equivalents in another tag, optionally ignoring some text")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(new JobParameters(parameterNames))
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			//Do we match the ECL?
			if (!checkEclCompliance(c, jobRun.getMandatoryParamValue(ATTRIBUTE_ECL))) {
				continue;
			}
			// What's the semantic tag here?
			String[] fsnParts = SnomedUtils.deconstructFSN(c.getFsn());
			String term = fsnParts[0].toLowerCase();
			
			for (String ignore : jobRun.getMandatoryParamValue(IGNORE_TEXT).split(",")) {
				term = term.replaceAll(ignore, "");
			}
			//Also take out any commas
			term = term.replaceAll("\\,", "");
			
			String semTag = fsnParts[1];
			if (StringUtils.isEmpty(semTag)) {
				continue;
			}
			
			//Is this one of our sources?  Check for matching targets
			if (semTag.equals(jobRun.getMandatoryParamValue(SOURCE_SEMTAG))) {
				List<Concept> sources = sourceMap.get(term);
				if (sources == null) {
					sources = new ArrayList<>();
					sourceMap.put(term, sources);
				}
				sources.add(c);
				if (targetMap.containsKey(term)) {
					report(targetMap.get(term), c, c.isActiveSafely()?"Y":"N");
					countIssue(c);
				}
			} else if (semTag.equals(jobRun.getMandatoryParamValue(TARGET_SEMTAG)) && StringUtils.isEmpty(c.getEffectiveTime())) {
				targetMap.put(term, c);
				if (sourceMap.containsKey(term)) {
					for (Concept source : sourceMap.get(term)) {
						report(c, source, source.isActiveSafely()?"Y":"N");
						countIssue(c);
					}
				}
			}
		}
	}

	private boolean checkEclCompliance(Concept c, String ecl) throws TermServerScriptException {
		String[] parts = ecl.split("=");
		Concept type = gl.getConcept(parts[0]);
		Concept value = gl.getConcept(parts[1]);
		return !c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, type, value, ActiveState.BOTH).isEmpty();
	}

}
