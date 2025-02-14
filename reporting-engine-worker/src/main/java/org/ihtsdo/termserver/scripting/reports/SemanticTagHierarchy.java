package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobCategory;
import org.snomed.otf.scheduler.domain.JobParameter;
import org.snomed.otf.scheduler.domain.JobParameters;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobType;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemanticTagHierarchy extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(SemanticTagHierarchy.class);

	Map<String, Map<String, Concept>> semanticTagHierarchy = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, BODY_STRUCTURE.toString());
		TermServerScript.run(SemanticTagHierarchy.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc
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
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		//Work out the path of semantic tags with examples
		LOGGER.info("Generating Semantic Tag Hierarchy report");
		Set<Concept> concepts = gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy);
		
		LOGGER.info("Examining all concepts to determine tag hierarchy");
		for (Concept c : concepts) {
			if (whiteListedConceptIds.contains(c.getId())) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			//Have we seen this semantic tag before?
			if (c.getFsn() == null) {
				LOGGER.warn(c + " encountered without a semantic tag");
				continue;
			}
			String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
			Map<String, Concept> childTags = semanticTagHierarchy.get(semTag);
			if (childTags == null) {
				childTags = new HashMap<>();
				semanticTagHierarchy.put(semTag, childTags);
			}
			
			//Record all the child semtags if not seen before
			for (Concept child : c.getDescendants(IMMEDIATE_CHILD, CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (whiteListedConceptIds.contains(child)) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				if (StringUtils.isEmpty(child.getFsn())) {
					LOGGER.warn ("Encountered concept without FSN during traversal: " + c);
					continue;
				}
				String childSemTag = SnomedUtilsBase.deconstructFSN(child.getFsn())[1];
				if (!childSemTag.equals(semTag) && !childTags.containsKey(childSemTag)) {
					childTags.put(childSemTag, child);
				}
			}
		}
		
		LOGGER.info("Encountered: " + semanticTagHierarchy.size() + " child tags across " + concepts.size() + " concepts");
		
		//Start with the top level hierarchy and go from there
		outputHierarchialStructure(SnomedUtilsBase.deconstructFSN(subHierarchy.getFsn())[1], 0);
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
