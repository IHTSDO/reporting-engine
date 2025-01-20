package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

/**
 * INFRA-9656 Request for list of concepts where causative agent and finding site are present but not grouped together.
 */
public class AttributesNotGroupedTogether extends TermServerReport implements ReportClass {

	public static String ATTRIBUTE_A = "Attribute A";
	public static String ATTRIBUTE_B = "Attribute B";
	public static String CHAR_TYPE = "Characteristic Type";
	public static String MUST_BOTH_EXIST = "Must Both Exist";
	public static String SELF_GROUPED_ONLY = "Self Grouped Only";
	
	private Concept attrA;
	private Concept attrB;
	private CharacteristicType charType;
	private boolean mustBothExist = false;
	boolean selfGroupedOnly = true;
		
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "*");
		params.put(CHAR_TYPE, "Inferred");
		params.put(ATTRIBUTE_A, OCCURRENCE.toString());
		params.put(ATTRIBUTE_B, FINDING_SITE.toString());
		params.put(MUST_BOTH_EXIST, "TRUE");
		params.put(SELF_GROUPED_ONLY, "TRUE");
		TermServerScript.run(AttributesNotGroupedTogether.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		subsetECL = run.getMandatoryParamValue(ECL);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		additionalReportColumns = "FSN,SemTag,DefStatus,EffectiveTime,Info,Model";
		attrA = gl.getConcept(jobRun.getMandatoryParamValue(ATTRIBUTE_A));
		attrB = gl.getConcept(jobRun.getMandatoryParamValue(ATTRIBUTE_B));
		charType = jobRun.getMandatoryParamValue(CHAR_TYPE).equals("Inferred")?CharacteristicType.INFERRED_RELATIONSHIP:CharacteristicType.STATED_RELATIONSHIP;
		mustBothExist = jobRun.getParamBoolean(MUST_BOTH_EXIST);
		selfGroupedOnly = jobRun.getParamBoolean(SELF_GROUPED_ONLY);
		super.postInit();
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(ATTRIBUTE_A).withType(JobParameter.Type.CONCEPT).withMandatory()
				.add(ATTRIBUTE_B).withType(JobParameter.Type.CONCEPT).withMandatory()
				.add(CHAR_TYPE).withType(JobParameter.Type.DROPDOWN).withOptions("Stated","Inferred").withDefaultValue("Inferred")
				.add(MUST_BOTH_EXIST).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(SELF_GROUPED_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List Attributes Not Grouped Together")
				.withDescription("This report lists all concepts where one of the two attributes exists, but not grouped with the other. For example, a causative agent not grouped with a finding site.  Optionally only pick up those cases when one of the attributes is in a group by itself (selected by default).")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subsetECL));
		subset.sort(Comparator.comparing(Concept::getFsnSafely));
		for (Concept c : subset) {
			//Firstly, are both attributes present?
			boolean attrAPresent = c.getRelationships(charType, attrA, ActiveState.ACTIVE).size() > 0;
			boolean attrBPresent = c.getRelationships(charType, attrB, ActiveState.ACTIVE).size() > 0;
			
			//Do we care?
			if (mustBothExist && (!attrAPresent || !attrBPresent)) {
				continue;
			}
			//Firstly do we have this attribute ungrouped?
			boolean attrAUngrouped = c.getRelationships(charType, attrA, UNGROUPED).size() > 0;
			boolean attrBUngrouped = c.getRelationships(charType, attrB, UNGROUPED).size() > 0;
			
			if (attrAUngrouped && !attrBUngrouped) {
				report(c, attrA.toStringPref() + " ungrouped without " + attrB.toStringPref(), c.toExpression(charType));
			} else if (!attrAUngrouped && attrBUngrouped) {
				report(c, attrB.toStringPref() + " ungrouped without " + attrA.toStringPref(), c.toExpression(charType));
			}
			
			//Now work through all groups to find A or B without the other
			for (RelationshipGroup g : c.getRelationshipGroups(charType)) {
				if (selfGroupedOnly && g.size() > 1) {
					continue;
				}
				boolean attrAGrouped = g.containsType(attrA);
				boolean attrBGrouped = g.containsType(attrB);
				boolean reported = false;
				if (attrAGrouped && !attrBGrouped) {
					reported = report(c, attrA.toStringPref() + " grouped without " + attrB.toStringPref(), c.toExpression(charType));
				} else if (!attrAGrouped && attrBGrouped) {
					reported = report(c, attrB.toStringPref() + " grouped without " + attrA.toStringPref(), c.toExpression(charType));
				}
				
				if (reported) {
					break; //Only report once per concept
				}
			}
		}
	}
	
	protected boolean report(Concept c, Object... details) throws TermServerScriptException {
		countIssue(c);
		String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		return super.report(c, defn, c.getEffectiveTime(), details);
	}

}
