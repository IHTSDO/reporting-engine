package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-138  For the subtypes of 236037000 |Incisional hernia (disorder)|
 * The changes required are:
 * - Due to attribute target value needs to be changed from 34896006 |Incision (procedure)| to 271618001 |Impaired wound healing (finding)|.
 * - After attribute target value needs to be changed from 387713003 |Surgical procedure (procedure)| to 34896006 |Incision (procedure)|.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QI138_IncisionalHerniaRemodel extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI138_IncisionalHerniaRemodel.class);

	Concept hWithO;
	Concept hWithG;
	Concept obstruction;
	
	Map<RelationshipTemplate, RelationshipTemplate> replacementMap;
	
	protected QI138_IncisionalHerniaRemodel(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		QI138_IncisionalHerniaRemodel fix = new QI138_IncisionalHerniaRemodel(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.classifyTasks = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		hWithO = gl.getConcept("772792004 |Obstruction co-occurrent and due to hernia of abdominal wall (disorder)|");
		hWithG = gl.getConcept("79990007 |Hernia, with gangrene (disorder)|");
		obstruction = gl.getConcept("26036001 |Obstruction (morphologic abnormality)|");
		subHierarchy = gl.getConcept("236037000 |Incisional hernia (disorder)|");
		
		replacementMap = new HashMap<>();
		
		RelationshipTemplate from = new RelationshipTemplate (DUE_TO, gl.getConcept("34896006 |Incision (procedure)|"));
		RelationshipTemplate to = new RelationshipTemplate (DUE_TO, gl.getConcept("271618001 |Impaired wound healing (finding)|"));
		replacementMap.put(from, to);

		from = new RelationshipTemplate (AFTER, gl.getConcept("387713003 |Surgical procedure (procedure)|"));
		to = new RelationshipTemplate (AFTER, gl.getConcept("34896006 |Incision (procedure)|"));
		replacementMap.put(from, to);
		
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = remodel(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int remodel(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Map.Entry<RelationshipTemplate, RelationshipTemplate> fromTo : replacementMap.entrySet()) {
			changesMade += replaceRelationship(t, c, fromTo.getKey(), fromTo.getValue());
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return asComponents(gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy));
	}
	
	protected Batch formIntoBatch (List<Component> allComponents) throws TermServerScriptException {
		//Form into 4 lists - simple, with obstruction, with gangrene, with both
		List<Component> groupOne = new ArrayList<>();
		List<Component> groupTwo = new ArrayList<>();
		List<Component> groupThree = new ArrayList<>();
		List<Component> groupFour = new ArrayList<>();
		List<List<Component>> buckets = new ArrayList<>();
		buckets.add(groupOne);
		buckets.add(groupTwo);
		buckets.add(groupThree);
		buckets.add(groupFour);
		
		//Sort into one of four buckets
		for (Component comp : allComponents) {
			Concept c = (Concept)comp;
			boolean isObstruction = gl.getDescendantsCache().getDescendantsOrSelf(hWithO).contains(c);
			//Or might have that morphology
			if (!isObstruction) {
				isObstruction = SnomedUtils.hasValue(CharacteristicType.INFERRED_RELATIONSHIP, c, obstruction);
			}
			boolean isGangrene = gl.getDescendantsCache().getDescendantsOrSelf(hWithG).contains(c);
			if (isObstruction && true) {
				//Is it both?
				if (isGangrene) {
					groupFour.add(c);
				} else {
					groupTwo.add(c);
				}
			} else if (isGangrene) {
				groupThree.add(c);
			} else {
				//Neither obstruction or gangrene
				groupOne.add(c);
			}
		}
		
		LOGGER.info("Counts for simple/obstruct/gangrene/both: " + groupOne.size() + "/" + groupTwo.size() + "/" + groupThree.size() + "/" + groupFour.size());
		return formIntoGroupedBatch(buckets);
	}

}
