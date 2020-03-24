package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * Devices-99 A set of tickets adding attributes to concepts based on matching terms in the FSN
 */
public class AddAttributeOnLexicalMatch extends BatchFix {
	
	Concept subHierarchy;
	Map<String, RelationshipTemplate> searchTermAttributeMap;

	protected AddAttributeOnLexicalMatch(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		AddAttributeOnLexicalMatch fix = new AddAttributeOnLexicalMatch(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.classifyTasks = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("260787004 |Physical object (physical object)|");
		
		//Order of terms is important since we'll search for the longest first to prevent
		//partial matches
		searchTermAttributeMap = new LinkedHashMap<>();
		//DEVICES-109
		searchTermAttributeMap.put("non-sterile", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("863956004 |Non-sterile (qualifier value)|")));
		searchTermAttributeMap.put("sterile", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("261029002 |Sterile (qualifier value)|")));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = addAttribute(task, loadedConcept);
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

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		for (Map.Entry<String, RelationshipTemplate> entry : searchTermAttributeMap.entrySet()) {
			if (c.getFsn().toLowerCase().contains(entry.getKey())) {
				Relationship attrib = entry.getValue().createRelationship(c, SELFGROUPED, null);
				return addRelationship(t, c, attrib);
			}
		}
		return NO_CHANGES_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			for (String searchTerm : searchTermAttributeMap.keySet()) {
				if (c.getFsn().toLowerCase().contains(searchTerm)) {
					processMe.add(c);
					break;
				}
			}
		}
		return processMe;
	}
}
