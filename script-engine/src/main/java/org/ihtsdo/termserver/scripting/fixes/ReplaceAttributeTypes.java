package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LOINC-387 A batch update is required for concepts/expressions on this tab as following:
 * - Change property from 118586006 |Ratio (property) (qualifier value)| to 784316008 |Arbitrary fraction (property) (qualifier value)|
 * - Add Attribute value 704325000 |Relative to (attribute)| 48583005 |Immunoglobulin E (substance)|
 *
 * INFRA-8111 for specified ECL selection map  
 *  424361007 |Using substance (attribute)| -> 363701004 |Direct substance (attribute)
 *  405814001 |Procedure site - Indirect (attribute)| -> 405813007 |Procedure site - Direct (attribute)|
 *  
 *  INFRA-8193  704324001 |Process output (attribute)| -> 1003735000 |Process acts on (attribute)| 
 *  
 *  INFRA-9154  405813007 |Procedure site - Direct|  ->  405814001 |Procedure site - Indirect|
 *  			363699004 |Direct device| -> 363710007 |Indirect device|
 *  			424361007 |Using substance| -> 363701004 |Direct substance|
 *  			Only where 260686004 |Method| = 129332006 |Irrigation - action|
 *  QI-1257    424226004 |Using device (attribute)| --> 363699004 |Direct device (attribute)|
 *  INFRA-13738 24876005 |Surgical approach (attribute)|  --> 116688005 |Procedure approach (attribute)|
 */
public class ReplaceAttributeTypes extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceAttributeTypes.class);

	String ecl = "< 71388002 |Procedure (procedure)| : 424876005 |Surgical approach (attribute)| = *";
	Map<Concept, Concept> replaceTypesMap;
	RelationshipTemplate addAttribute = null;
	RelationshipTemplate whereAttributePresent = null;
	
	protected ReplaceAttributeTypes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceAttributeTypes fix = new ReplaceAttributeTypes(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_UPDATES);
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.classifyTasks = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		replaceTypesMap = new HashMap<>();
		replaceTypesMap.put(gl.getConcept("424876005 |Surgical approach (attribute)|"),
				gl.getConcept("116688005 |Procedure approach (attribute)|"));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = replaceAttributeTypes(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int replaceAttributeTypes(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//Work group at a time and ensure the group contains the required attribute - if specified
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			changesMade += replaceAttributeInGroup(t, c, g);
		}
		
		if (addAttribute != null) {
			changesMade += addRelationship(t, c, addAttribute, SnomedUtils.getFirstFreeGroup(c));
		}
		return changesMade;
	}

	private int replaceAttributeInGroup(Task t, Concept c, RelationshipGroup g) throws TermServerScriptException {
		int changesMade = 0;
		int replacedAttributesEncouteredInGroup = 0; //Either because we did the replacement or because they're already present
		if (whereAttributePresent == null || g.containsTypeValue(whereAttributePresent)) {
			for (Concept targetType : replaceTypesMap.keySet()) {
				for (Relationship r : g.getRelationships(ActiveState.ACTIVE)) {
					if (r.getType().equals(targetType)) {
						Relationship replacement = r.clone();
						replacement.setType(replaceTypesMap.get(targetType));
						changesMade += replaceRelationship(t, c, r, replacement);
						replacedAttributesEncouteredInGroup++;
					} else if (replaceTypesMap.values().contains(r.getType())) {
						replacedAttributesEncouteredInGroup++;

					}
				}
			}
		}
		if (replacedAttributesEncouteredInGroup > 1) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Duplicate attributes encountered in concept", c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		return findConcepts(ecl).stream()
			.filter(this::inScope)
			.filter(this::meetsProcessingCriteria)
			.sorted(SnomedUtils::compareSemTagFSN)
				.map(c -> (Component)c)
			.toList();
	}

	private boolean meetsProcessingCriteria(Concept c) {
		if (whereAttributePresent == null || !c.getRelationships(whereAttributePresent, ActiveState.ACTIVE).isEmpty()) {
			for (Concept targetType : replaceTypesMap.keySet()) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(targetType)) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
