package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
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
 */
public class ReplaceAttributeTypes extends BatchFix {
	
	String ecl = "<< 226321006 |Amino acid intake (observable entity)| OR << 788472008 |Carbohydrate intake (observable entity)| OR << 792882006 |Estimated intake of protein and protein derivative in 24 hours (observable entity)| OR << 226354008 |Water intake (observable entity)| OR 788662004 |Estimated intake of niacin in 24 hours (observable entity)| OR << 870372004 |Fat and oil intake (observable entity)| OR << 226352007 |Mineral intake (observable entity)| OR << 226349004 |Vitamin intake (observable entity)| OR <<226320007 |Nutrient intake (observable entity)| ";
	Map<Concept, Concept> replaceTypesMap;
	RelationshipTemplate addAttribute;
	
	protected ReplaceAttributeTypes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReplaceAttributeTypes fix = new ReplaceAttributeTypes(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = false;
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
		/*replaceTypesMap.put(gl.getConcept("118586006 |Ratio (property) (qualifier value)| "), 
				gl.getConcept("784316008 |Arbitrary fraction (property) (qualifier value)|"));
		addAttribute = new RelationshipTemplate(gl.getConcept("704325000 |Relative to (attribute)| "),
				gl.getConcept("48583005 |Immunoglobulin E (substance)|"));
		replaceTypesMap.put(gl.getConcept("424361007 |Using substance (attribute)|"), 
				gl.getConcept("363701004 |Direct substance (attribute)|"));
		replaceTypesMap.put(gl.getConcept("405814001 |Procedure site - Indirect (attribute)|"), 
				gl.getConcept("405813007 |Procedure site - Direct (attribute)|"));*/
		replaceTypesMap.put(gl.getConcept("704324001 |Process output (attribute)| "), 
				gl.getConcept("1003735000 |Process acts on (attribute)| "));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = switchValues(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (Concept targetType : replaceTypesMap.keySet()) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(targetType)) {
					Relationship replacement = r.clone();
					replacement.setType(replaceTypesMap.get(targetType));
					changesMade += replaceRelationship(t, c, r, replacement);
				}
			}
		}
		
		if (addAttribute != null) {
			changesMade += addRelationship(t, c, addAttribute, SnomedUtils.getFirstFreeGroup(c));
		}
		return changesMade;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		info("Identifying concepts to process");
		return findConcepts(ecl).stream()
			.filter(c -> hasTargetType(c))
			.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
			.collect(Collectors.toList());
	}

	private boolean hasTargetType(Concept c) {
		for (Concept targetType : replaceTypesMap.keySet()) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(targetType)) {
					return true;
				}
			}
		}
		return false;
	}

}
