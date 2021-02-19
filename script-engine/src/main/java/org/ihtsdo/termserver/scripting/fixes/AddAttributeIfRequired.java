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
 * INFRA-5176 Add an attribute to a given ECL substrate where required
 * INFRA-5236 Add DueTo to Abrasions
 * QI-731 Add DueTo = Traumatic Event for all open fractures
 * QI-802 Add 42752001 |Due to (attribute)| = 1148742001 |Intentional event (event)|
 *    to all << 59274003 |Intentional drug overdose (disorder)| 
 */
public class AddAttributeIfRequired extends BatchFix {
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;
	
	protected AddAttributeIfRequired(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		AddAttributeIfRequired fix = new AddAttributeIfRequired(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
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
		/*INFRA-5176
		subHierarchyECL = "<< 312608009 |Laceration - injury (disorder)| MINUS << 262541004 |Superficial laceration (disorder)|";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("773760007 |Traumatic event (event)|"));
		
		//INFRA-5236
		subsetECL = "<<399963005 |Abrasion (disorder)|";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("773760007 |Traumatic event (event)|"));
		
		//QI-731
		subsetECL = "<< 439987009 |Open fracture of bone (disorder)| : [0..0] 42752001 |Due to (attribute)| = 773760007 |Traumatic event (event)|";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("773760007 |Traumatic event (event)|"));
		
		//QI-799
		//subsetECL = "214503008 OR 214504002 OR 214507009 OR 214509007 OR 214505001 OR 214508004 OR 214511003 OR 214512005 OR 214510002 OR 242188004 OR 274917005 OR 216233003 OR 216237002 OR 216243000 OR 216239004 OR 216236006 OR 216241003 OR 216242005";
		subsetECL = "<< 72431002 |Accidental poisoning (disorder)| MINUS (214503008 OR 214504002 OR 214507009 OR 214509007 OR 214505001 OR 214508004 OR 214511003 OR 214512005 OR 214510002 OR 242188004 OR 274917005 OR 216233003 OR 216237002 OR 216243000 OR 216239004 OR 216236006 OR 216241003 OR 216242005)";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("418019003 |Accidental event (event)|"));
		
		//QI-800
		subsetECL = "<< 410061008 |Intentional poisoning (disorder)|";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("1148742001 |Intentional event (event)|"));
		
		//QI-801
		subsetECL = "<< 59369008 |Accidental drug overdose (disorder)|";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("418019003 |Accidental event (event)|"));
		*/	
		//QI-802
		subsetECL = "<< 59274003 |Intentional drug overdose (disorder)|";
		relTemplate = new RelationshipTemplate(DUE_TO, gl.getConcept("1148742001 |Intentional event (event)|"));
		
		exclusions = new HashSet<>();
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
		if (isExcluded(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			Relationship attrib = relTemplate.createRelationship(c, SELFGROUPED, null);
			return addRelationship(t, c, attrib);
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
		return new ArrayList<>(findConcepts(subsetECL));
	}
}
