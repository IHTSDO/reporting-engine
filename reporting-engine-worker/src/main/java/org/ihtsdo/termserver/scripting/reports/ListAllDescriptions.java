package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
/**
 * See FD#25496
 */
public class ListAllDescriptions extends TermServerReport implements ReportClass {
	Set<Concept> alreadyReported = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(SUB_HIERARCHY, "43959009 |Cataract of eye due to diabetes mellitus (disorder)|");
		//params.put(SUB_HIERARCHY, "38199008 |Tooth structure (body structure)|");
		params.put(SUB_HIERARCHY, "25093002 |Disorder of eye due to diabetes mellitus (disorder)|");
		TermServerReport.run(ListAllDescriptions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns="FSN, SemTag, Hierarchy, Defn, Parents, Description";
		super.init(run);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withMandatory().build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Descriptions")
				.withDescription("This report lists all descriptions in a given hierarchy." +
						"The issues count will show the number of concepts reported.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		Concept hierarchy = gl.getConcept(jobRun.getMandatoryParamValue(SUB_HIERARCHY));
		listDescriptionsAndChildren(hierarchy, 0);
	}

	private void listDescriptionsAndChildren(Concept c, int depth) throws TermServerScriptException {
		//Have we already seen this concept?
		if (alreadyReported.contains(c)) {
			return;
		}
		
		String indent = getIndent(depth);
		String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		String parents = getParentsStr(c);
		
		report (c, indent, defn, parents);
		incrementSummaryInformation("Concepts reported");
		countIssue(c);
		
		List<Description> descriptions = c.getDescriptions(ActiveState.ACTIVE);
		SnomedUtils.prioritise(descriptions);
		for (Description d : descriptions) {
			report ((Concept)null, "", "", "", d);
		}
		
		//Now recurse down into all children
		for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			listDescriptionsAndChildren(child, depth + 1);
		}
	}

	private String getParentsStr(Concept c) {
		Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		return parents.stream().map(p->p.toString())
				.collect(Collectors.joining(",\n"));
	}

	private String getIndent(int depth) {
		if (depth == 0) {
			return "";
		} 
		StringBuffer sb = new StringBuffer("|");
		for (int i = 0; i < depth ; i++) {
			sb.append("_");
		}
		return sb.toString();
	}

}
