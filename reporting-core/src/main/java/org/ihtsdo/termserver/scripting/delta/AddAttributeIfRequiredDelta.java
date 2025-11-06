package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.HashSet;
import java.util.Set;

public class AddAttributeIfRequiredDelta extends DeltaGenerator {
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;

	private static final int BATCH_SIZE = 271;
	private int lastBatchSize = 0;

	public static void main(String[] args) throws TermServerScriptException {
		AddAttributeIfRequiredDelta delta = new AddAttributeIfRequiredDelta();
		try {
			delta.runStandAlone = true;
			delta.additionalReportColumns = "Action Detail";
			delta.newIdsRequired = false;
			delta.init(args);
			delta.loadProjectSnapshot(true);
			delta.postLoadInit();
			delta.process();
			delta.createOutputArchive(false, delta.lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//INFRA-12889
		subsetECL = "(^ 723264001) MINUS ( << 423857001 |Structure of half of body lateral to midsagittal plane (body structure)| MINUS ( * : 272741003 |Laterality (ttribute)| = ( 7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier alue)| )))";
		relTemplate = new RelationshipTemplate(gl.getConcept("272741003 |Laterality (attribute)|"), gl.getConcept("182353008 |Side|"));

		exclusions = new HashSet<>();
		super.postInit(GFOLDER_ADHOC_UPDATES);
	}

	@Override
	protected void process() throws TermServerScriptException {
		int conceptsInThisBatch = 0;
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL))) {
				int changesMade = addAttribute(c);
				if (changesMade > 0) {
					outputRF2(c);
					conceptsInThisBatch++;
					if (conceptsInThisBatch >= BATCH_SIZE) {
						createOutputArchive(false, conceptsInThisBatch);
						gl.setAllComponentsClean();
						outputDirName = "output"; //Reset so we don't end up with _1_1_1
						initialiseOutputDirectory();
						initialiseFileHeaders();
						conceptsInThisBatch = 0;
					}
				}

		}
		lastBatchSize = conceptsInThisBatch;
	}

	private int addAttribute(Concept c) throws TermServerScriptException {
		if (isExcluded(c)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			Relationship attrib = relTemplate.createRelationship(c, SELFGROUPED, null);
			return replaceRelationship((Task)null, c, attrib.getType(), attrib.getTarget(), attrib.getGroupId(), RelationshipTemplate.Mode.UNIQUE_TYPE_VALUE_ACROSS_ALL_GROUPS); //Allow other relationships of the same type, but not typeValue

		}
		return NO_CHANGES_MADE;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		for (String exclusionWord : exclusions) {
			if (fsn.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}
}
