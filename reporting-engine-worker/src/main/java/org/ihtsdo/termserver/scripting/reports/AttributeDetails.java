package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * RP-285
 * CDI-25 Add column for concrete values.
 **/
public class AttributeDetails extends TermServerReport implements ReportClass {
		public static final String COMPACT = "Compact Report Format";
		public static final String INCLUDE_WORD = "Include word";
		public static final String EXCLUDE_WORD = "Exclude word";
		boolean compactReport = true;
		String includeWord;
		String excludeWord;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "< 404684003 |Clinical finding| : { 363698007 |Finding site| = << 39057004 |Pulmonary valve structure| , 116676008 |Associated morphology| = << 415582006 |Stenosis| }");
		params.put(COMPACT, "true");
		params.put(INCLUDE_WORD, "Pulmonic");
		TermServerScript.run(AttributeDetails.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		subsetECL = run.getMandatoryParamValue(ECL);
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
			additionalReportColumns = "FSN,SemTag,DefStatus,RelType,Stated/Inferred,Destination SCTID,Destination FSN,Destination SemTag, Concrete Value";
		}
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(COMPACT).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(true)
				.add(INCLUDE_WORD).withType(JobParameter.Type.STRING)
				.add(EXCLUDE_WORD).withType(JobParameter.Type.STRING)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Attribute Detail")
				.withDescription("This report lists all concepts matching the specified ECL - filtered based on term - along with their attributes" + 
				" in a compact or expanded report format.  Note that concepts with no attributes will not be listed")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withExpectedDuration(30)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subsetECL));
		subset.sort(Comparator.comparing(Concept::getFsn));
		for (Concept c : subset) {
			if (!isLexicalMatch(c) || countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0) {
				continue;
			}
			
			String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			//Are we working in verbose or compact mode?
			if (compactReport) {
				String expression = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				report(c, defStatus, expression);
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
					report(c, r, defStatus, characteristicStr);
				}
				//Are there any relationships which are only stated?
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(IS_A)) {
						continue;
					}
					if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
						report(c, r, defStatus, "S");
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

	private void report(Concept c, Relationship r, String defStatus, String characteristicStr) throws TermServerScriptException {
		String typePT = r.getType().getPreferredSynonym();
		if (r.isConcrete()) {
			report(c, defStatus, typePT, characteristicStr, "-", "-", "-", r.getConcreteValue());
		} else {
			report(c, defStatus, typePT, characteristicStr, r.getTarget().getConceptId(), r.getTarget().getFsn(), SnomedUtilsBase.deconstructFSN(r.getTarget().getFsn())[1], "-");
		}
		countIssue(c);
	}

}
