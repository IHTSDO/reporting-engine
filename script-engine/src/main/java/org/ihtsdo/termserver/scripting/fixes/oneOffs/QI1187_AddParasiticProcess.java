package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate.Mode;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class QI1187_AddParasiticProcess extends BatchFix {
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;
	//private RelationshipTemplate workAroundToRemove;
	//String inclusionText = "primary";
	//private Map<Concept, Concept> replaceValuesMap;
	Collection<Concept> matchValues;
	
	protected QI1187_AddParasiticProcess(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		QI1187_AddParasiticProcess fix = new QI1187_AddParasiticProcess(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.runStandAlone = true;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subsetECL = "(< 404684003 |Clinical finding| : { 246075003 |Causative agent (attribute)| = ( <<417396000 |Kingdom Protozoa (organism)| OR " + 
				"<<441649000 |Class Cestoda and/or Class Trematoda and/or Phylum Nemata (organism)| OR " + 
				"<<11950008 |Phylum Acanthocephala (organism)| OR " + 
				"<<106762008 |Phylum Arthropoda (organism)| ) } )" +
				" MINUS " +
				" (< 404684003 |Clinical finding| : { 246075003 |Causative agent (attribute)| = *, 370135005 |Pathological process (attribute)|= 442614005 |Parasitic process (qualifier value)| })";
		relTemplate = new RelationshipTemplate(PATHOLOGICAL_PROCESS, gl.getConcept("442614005 |Parasitic process (qualifier value)| "));
		//workAroundToRemove = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("86049000 |Malignant neoplasm, primary (morphologic abnormality)|"));
		exclusions = new HashSet<>();
		//exclusions.add("metastasis");
		//replaceValuesMap = new HashMap<>();
		//replaceValuesMap.put(gl.getConcept("86049000 |Malignant neoplasm, primary|"), gl.getConcept("1240414004 |Malignant neoplasm morphology|"));
		matchValues = findConcepts( " <<417396000 |Kingdom Protozoa (organism)| OR " + 
				"<<441649000 |Class Cestoda and/or Class Trematoda and/or Phylum Nemata (organism)| OR" + 
				"<<11950008 |Phylum Acanthocephala (organism)| OR" + 
				"<<106762008 |Phylum Arthropoda (organism)|" );
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			/*if (concept.getId().equals("285645000")) {
				debug("here");
			}*/
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = addAttribute(task, loadedConcept);
			if (changesMade > 0) {
				String expression = loadedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				updateConcept(task, loadedConcept, info);
				report (task, loadedConcept, Severity.NONE, ReportActionType.INFO, expression);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		if (isExcluded(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			List<RelationshipGroup> groupsToProcess = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, CAUSE_AGENT);
			for (RelationshipGroup g : groupsToProcess) {
				//Is the Causative Agent one of the ones we're interested in?
				Concept ca = g.getValueForType(CAUSE_AGENT);
				if (!matchValues.contains(ca)) {
					continue;
				}
				Relationship attrib = relTemplate.createRelationship(c, g.getGroupId(), null);
				changesMade += addRelationship(t, c, attrib, Mode.REPLACE_TYPE_IN_THIS_GROUP);
			}
			
			return changesMade;
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

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return findConcepts(subsetECL).parallelStream()
				//.filter(c -> c.getFsn().toLowerCase().contains(inclusionText))
				.filter(c -> !isExcluded(c))
				//.filter(c -> !gl.isOrphanetConcept(c))
				//.filter(c -> c.getRelationships(relTemplate, ActiveState.ACTIVE).size() == 0)
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}
}
