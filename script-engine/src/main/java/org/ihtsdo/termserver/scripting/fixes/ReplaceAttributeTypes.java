package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * LOINC-387 A batch update is required for concepts/expressions on this tab as following:
 * - Change property from 118586006 |Ratio (property) (qualifier value)| to 784316008 |Arbitrary fraction (property) (qualifier value)|
 * - Add Attribute value 704325000 |Relative to (attribute)| 48583005 |Immunoglobulin E (substance)|
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
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceAttributeTypes extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceAttributeTypes.class);

	String ecl = "<< 71388002 |Procedure (procedure)| : 425391005 |Using access device (attribute)| = *";
	Map<Concept, Concept> replaceTypesMap;
	RelationshipTemplate addAttribute = null;
	RelationshipTemplate whereAttributePresent = null;
	
	protected ReplaceAttributeTypes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceAttributeTypes fix = new ReplaceAttributeTypes(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		replaceTypesMap = new HashMap<>();
		replaceTypesMap.put(gl.getConcept("425391005 |Using access device (attribute)|"),
				gl.getConcept("424226004 |Using device (attribute)|"));
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
	
	private int replaceAttributeTypes(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		boolean includeDuplicationWarning = false;
		//Work group at a time and ensure the group contains the required attribute - if specified
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
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
				includeDuplicationWarning = true;
			}
		}
		
		if (addAttribute != null) {
			changesMade += addRelationship(t, c, addAttribute, SnomedUtils.getFirstFreeGroup(c));
		}

		if (includeDuplicationWarning) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Duplicate attributes encountered in concept", c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		}
		return changesMade;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		
		//First report those which we are NOT going to process
		/*findConcepts(ecl).stream()
				.filter(c -> !meetsProcessingCriteria(c))
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.forEach(c -> { 
					try {
						report((Task)null, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Concept did not meeting processing criteria", c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
					} catch (TermServerScriptException e) {
						LOGGER.error("Exception encountered",e);
					}
				});*/
		
		return findConcepts(ecl).stream()
			.filter(c -> meetsProcessingCriteria(c))
			.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
			.collect(Collectors.toList());
	}

	private boolean meetsProcessingCriteria(Concept c) {
		if (whereAttributePresent == null || c.getRelationships(whereAttributePresent, ActiveState.ACTIVE).size() > 0) {
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
