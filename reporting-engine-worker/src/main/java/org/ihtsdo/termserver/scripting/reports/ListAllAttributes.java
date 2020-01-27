package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

/**
 * RP-276 
 * Similar to the existing QI report, Farzaneh and I will need an additional report where we can enter an SCTID for the subhierarchy of interest and get the following output:
 *
 * SCTID (for all stated or inferred descendants)
 * FSN
 * Rel Type (stated only)
 * AttValueSCTID
 * AttValueFSN (where semantic tag is substance, medicinal product, medicinal product form, product, or clinical drug) - note that the initial reports will only have substance semantic tag
 */
public class ListAllAttributes extends TermServerReport implements ReportClass {
		public static final String COMPACT = "Compact Report Format";
		public static final String TARGET_VALUE_PROPERTY = "Target Value Property";
		public static final String INCLUDE_IS_A = "Include Is A";
		boolean compactReport = true;
		boolean includeIsA = true;
		Concept targetValueProperty = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(ECL, "<<  " + SUBSTANCE);
		//params.put(ECL, "<< 419199007 |Allergy to substance (finding)|");
		//params.put(ECL, "<< 282100009 |Adverse reaction caused by substance (disorder)|");
		params.put(ECL, "< 404684003 |Clinical finding| : { 363698007 |Finding site| = << 39057004 |Pulmonary valve structure| , 116676008 |Associated morphology| = << 415582006 |Stenosis| }");
		params.put(COMPACT, "true");
		params.put(INCLUDE_IS_A, "false");
		params.put(TARGET_VALUE_PROPERTY, IS_MODIFICATION_OF.toString());
		TermServerReport.run(ListAllAttributes.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		subHierarchyECL = run.getMandatoryParamValue(ECL);
		compactReport = run.getParameters().getMandatoryBoolean(COMPACT);
		includeIsA = run.getParameters().getMandatoryBoolean(INCLUDE_IS_A);
		String targetValuePropertyStr = run.getParamValue(TARGET_VALUE_PROPERTY);
		if (!StringUtils.isEmpty(targetValuePropertyStr)) {
			targetValueProperty = gl.getConcept(targetValuePropertyStr);
		}
		if (compactReport) {
			additionalReportColumns = "FSN,SemTag,DefStatus,SCT Expression,Target Property Present";
		} else {
			additionalReportColumns = "FSN,SemTag,DefStatus,RelType,Target Property Present,Stated/Inferred,Value SCTID,Value FSN,Value SemTag";
		}
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(COMPACT).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(true)
				.add(INCLUDE_IS_A).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(TARGET_VALUE_PROPERTY).withType(JobParameter.Type.CONCEPT)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Attributes")
				.withDescription("This report lists all concepts matching the specified ECL, along with their attributes" + 
				" in a compact or expanded report format. The target value property can be specified to determine something additional " +
				" about the target value - the primary use case for this is detecting the presence of the modification attribute on a substance " +
				" used as a product ingredient or causative agent. The issue count shows the number of data rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subHierarchyECL));
		subset.sort(Comparator.comparing(Concept::getFsn));
		for (Concept c : subset) {
			if (!includeIsA && countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0) {
				continue;
			}
			
			boolean targetValuePropertyPresent = false;
			String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			//Are we working in verbose or compact mode?
			if (compactReport) {
				String expression = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				if (targetValueProperty != null) {
					targetValuePropertyPresent = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE).stream()
							.map(r -> checkTargetValuePropertyPresent(r.getTarget()))
									.filter (b -> (b == true))
									.count() > 0;
				}
				report (c, defStatus, expression, targetValuePropertyPresent?"Y":"N");
				countIssue(c);
			} else {
				String characteristicStr = "";
				for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (!includeIsA && r.getType().equals(IS_A)) {
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
					if (!includeIsA && r.getType().equals(IS_A)) {
						continue;
					}
					if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
						report (c, r, defStatus, "S");
					}
				}
			}
		}
	}
	
	private void report (Concept c, Relationship r, String defStatus, String characteristicStr) throws TermServerScriptException {
		String targetAttribPresent = checkTargetValuePropertyPresent(r.getTarget())?"Y":"N";
		String typePT = r.getType().getPreferredSynonym();
		String fsn = r.getTarget().getFsn();
		String semTag = SnomedUtils.deconstructFSN(fsn)[1];
		report (c, defStatus, typePT, targetAttribPresent, characteristicStr, r.getTarget().getConceptId(), fsn, semTag);
		countIssue(c);
	}

	private boolean checkTargetValuePropertyPresent(Concept v) {
		if (targetValueProperty != null) {
			for (Relationship vR : v.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (vR.getType().equals(targetValueProperty)) {
					return true;
				}
			}
		}
		return false;
	}

}
