package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class AttributeTypeUsage extends TermServerReport implements ReportClass {
	public static final String TARGET_ATTRIBUTE_TYPE = "Attribute Type";
	private Concept targetAttributeType;
	private CharacteristicType charType = CharacteristicType.STATED_RELATIONSHIP;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "*");
		params.put(TARGET_ATTRIBUTE_TYPE, "766939001 |Plays role (attribute)|");
		TermServerScript.run(AttributeTypeUsage.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		additionalReportColumns="FSN, SemTag, Relationship";
		subsetECL = run.getMandatoryParamValue(ECL);
		super.init(run);
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		targetAttributeType = gl.getConcept(jobRun.getMandatoryParamValue(TARGET_ATTRIBUTE_TYPE));
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(TARGET_ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Attribute Type Usage")
				.withDescription("This report lists all instances of a particular attribute type being used.") 
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subsetECL));
		subset.sort(Comparator.comparing(Concept::getFsn));
		for (Concept c : subset) {
			for (Relationship r : c.getRelationships(charType, targetAttributeType, ActiveState.ACTIVE)) {
				report(c, r);
				countIssue(c);
			}
		}
	}
	
}
