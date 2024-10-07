package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class TextDefinitionsPresentAndMissing extends TermServerReport implements ReportClass {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, Object> params = new HashMap<>();
		params.put(ECL, "<< 1222584008 |American Joint Committee on Cancer allowable value|");
		TermServerScript.run(TextDefinitionsPresentAndMissing.class, params, args);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports

		String[] columnHeadings = new String[] {"Sctid, FSN, SemTag, Text Definitions",
				"Sctid, FSN"};
		String[] tabNames = new String[] {	"Text Definitions",
											"Missing Text Definitions"};

		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.STRING).withDescription("Optional. Will show the attribute values per concept for the specified attribute type.  For example in Substances, show me all concepts that are used as a target for 738774007 |Is modification of (attribute)| by specifying that attribute type in this field.")
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Text Definitions - Present and Missing")
				.withDescription("This report lists all text definitions for concepts in the given ECL selection and also, on the 2nd tab, all concepts that do not have a text definition.  The issue count is the number of concepts which do not have a text definition.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest = gl.getAllConcepts();
		if (subsetECL != null) {
			conceptsOfInterest = findConcepts(subsetECL);
		}
		conceptsOfInterest = SnomedUtils.sortActive(conceptsOfInterest);
		
		for (Concept c : conceptsOfInterest) {
			if (c.isActiveSafely()) {
				if (whiteListedConceptIds.contains(c.getId())) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				//Collect any active text definitions
				String textDefinitions = c.getDescriptions(ActiveState.ACTIVE).stream()
						.filter(d -> d.getType().equals(DescriptionType.TEXT_DEFINITION))
						.map(Description::getTerm)
						.collect(Collectors.joining(", \n"));
				
				//Did we get anything?  Send concept to 2nd tab if not
				if (StringUtils.isEmpty(textDefinitions)) {
					report(SECONDARY_REPORT, c.getId(), c.getFsn());
					countIssue(c);
				} else {
					report(c, textDefinitions);
				}
			}
		}
	}

}
