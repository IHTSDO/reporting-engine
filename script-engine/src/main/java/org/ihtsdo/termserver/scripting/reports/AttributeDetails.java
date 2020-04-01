package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

/**
 * RP-285
**/
public class AttributeDetails extends TermServerReport implements ReportClass {
		public static final String COMPACT = "Compact Report Format";
		public static final String INCLUDE_WORD = "Include word";
		public static final String EXCLUDE_WORD = "Exclude word";
		boolean compactReport = true;
		String includeWord;
		String excludeWord;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "< 404684003 |Clinical finding| : { 363698007 |Finding site| = << 39057004 |Pulmonary valve structure| , 116676008 |Associated morphology| = << 415582006 |Stenosis| }");
		params.put(COMPACT, "true");
		params.put(INCLUDE_WORD, "Pulmonic");
		TermServerReport.run(AttributeDetails.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		subHierarchyECL = run.getMandatoryParamValue(ECL);
		compactReport = run.getParameters().getMandatoryBoolean(COMPACT);
		includeWord = run.getParamValue(INCLUDE_WORD);
		if (includeWord != null) {
			includeWord = includeWord.toLowerCase();
		}
		
		excludeWord = run.getParamValue(EXCLUDE_WORD);
		if (excludeWord != null) {
			excludeWord = excludeWord.toLowerCase();
		}
		
		if (compactReport) {
			additionalReportColumns = "FSN,SemTag,DefStatus,SCT Expression";
		} else {
			additionalReportColumns = "FSN,SemTag,DefStatus,RelType,Stated/Inferred,Value SCTID,Value FSN,Value SemTag";
		}
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(COMPACT).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(true)
				.add(INCLUDE_WORD).withType(JobParameter.Type.STRING)
				.add(EXCLUDE_WORD).withType(JobParameter.Type.STRING)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Attribute Detail")
				.withDescription("This report lists all concepts matching the specified ECL - filtered based on term - along with their attributes" + 
				" in a compact or expanded report format.  Note that concepts with no attributes will not be listed")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subHierarchyECL));
		subset.sort(Comparator.comparing(Concept::getFsn));
		for (Concept c : subset) {
			if (!isLexicalMatch(c) || countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0) {
				continue;
			}
			
			String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			//Are we working in verbose or compact mode?
			if (compactReport) {
				String expression = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				report (c, defStatus, expression);
				countIssue(c);
			} else {
				String characteristicStr = "";
				for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(IS_A)) {
						continue;
					}
					characteristicStr = "I";
					if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() > 0) {
						characteristicStr += " + S";
					}
					report (c, r, defStatus, characteristicStr);
				}
				//Are there any relationships which are only stated?
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(IS_A)) {
						continue;
					}
					if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
						report (c, r, defStatus, "S");
					}
				}
			}
		}
	}
	
	private boolean isLexicalMatch(Concept c) {
		if (!StringUtils.isEmpty(includeWord)) {
			boolean containsWord = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.anyMatch(t -> t.toLowerCase().contains(includeWord));
			if (!containsWord) {
				return false;
			}
		}
		
		//For exclusions we'll only consider FSN and PT
		if (!StringUtils.isEmpty(excludeWord)) {
			boolean containsWord = c.getDescriptions(ActiveState.ACTIVE).stream()
					.filter(d -> d.isPreferred())
					.map(d -> d.getTerm())
					.anyMatch(t -> t.toLowerCase().contains(excludeWord));
			if (containsWord) {
				return false;
			}
		}
		return true;
	}

	private void report (Concept c, Relationship r, String defStatus, String characteristicStr) throws TermServerScriptException {
		String typePT = r.getType().getPreferredSynonym();
		String fsn = r.getTarget().getFsn();
		String semTag = SnomedUtils.deconstructFSN(fsn)[1];
		report (c, defStatus, typePT, characteristicStr, r.getTarget().getConceptId(), fsn, semTag);
		countIssue(c);
	}

}
