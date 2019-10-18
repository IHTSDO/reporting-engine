package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobCategory;
import org.snomed.otf.scheduler.domain.JobParameter;
import org.snomed.otf.scheduler.domain.JobParameters;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobType;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

public class SemanticTagHierarchy extends TermServerReport implements ReportClass {
	
	Map<String, Map<String, Concept>> semanticTagHierarchy = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, BODY_STRUCTURE.toString());
		TermServerReport.run(SemanticTagHierarchy.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		headers="SemTag, As used by";
		additionalReportColumns = "";
		super.init(run);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withMandatory().build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Semantic Tag Hierarchy")
				.withDescription("This report lists all semantic tags used in the specified subhierarchy. " +
						"Note that since this report is not listing any problems, the 'Issues' count will always be 0.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		//Work out the path of semantic tags with examples
		info ("Generating Semantic Tag Hierarchy report");
		Set<Concept> concepts = gl.getDescendantsCache().getDescendentsOrSelf(subHierarchy);
		
		info ("Examining all concepts to determine tag hierarchy");
		for (Concept c : concepts) {
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			//Have we seen this semantic tag before?
			if (c.getFsn() == null) {
				warn(c + " encountered without a semantic tag");
				continue;
			}
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			Map<String, Concept> childTags = semanticTagHierarchy.get(semTag);
			if (childTags == null) {
				childTags = new HashMap<>();
				semanticTagHierarchy.put(semTag, childTags);
			}
			
			//Record all the child semtags if not seen before
			for (Concept child : c.getDescendents(IMMEDIATE_CHILD, CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (whiteListedConcepts.contains(child)) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				if (StringUtils.isEmpty(child.getFsn())) {
					warn ("Encountered concept without FSN during traversal: " + c);
					continue;
				}
				String childSemTag = SnomedUtils.deconstructFSN(child.getFsn())[1];
				if (!childSemTag.equals(semTag) && !childTags.containsKey(childSemTag)) {
					childTags.put(childSemTag, child);
				}
			}
		}
		
		info ("Encountered: " + semanticTagHierarchy.size() + " child tags across " + concepts.size() + " concepts");
		
		//Start with the top level hierarchy and go from there
		outputHierarchialStructure(SnomedUtils.deconstructFSN(subHierarchy.getFsn())[1], 0);
	}

	private void outputHierarchialStructure(String semTag, int level) throws TermServerScriptException {
		if (level == 0) {
			writeToReportFile(0, semTag);
		}
		level++;
		String indent = StringUtils.repeat("-- ", level);
		
		if (level > 10) {
			writeToReportFile(0, indent + "Recursion limit reached");
		} else {
			if (semanticTagHierarchy.size() == 0) {
				writeToReportFile(0, indent + "No child tags detected.");
			} else if (semanticTagHierarchy.get(semTag) == null) {
				writeToReportFile(0, indent + "Unknown semantic tag encountered: '" + semTag + "'");
			} else {
				for (Map.Entry<String, Concept> childTag : semanticTagHierarchy.get(semTag).entrySet()) {
					writeToReportFile(0, indent + childTag.getKey() + COMMA + childTag.getValue());
					outputHierarchialStructure(childTag.getKey(), level);
				}
			}
		}
	}

}
