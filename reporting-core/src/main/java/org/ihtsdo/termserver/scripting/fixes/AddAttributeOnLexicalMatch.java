package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * DEVICES-99 A set of tickets adding attributes to concepts based on matching terms in the FSN
 * DEVICES-121 Likewise
 * INFRA-5794 Add parent to 'Idiopathic' if required
 */
public class AddAttributeOnLexicalMatch extends BatchFix {
	
	private Concept subHierarchy;
	private Map<String, RelationshipTemplate> searchTermAttributeMap;
	private Set<String> exclusions;
	private Set<Concept> acceptableAlternatives;

	protected AddAttributeOnLexicalMatch(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		AddAttributeOnLexicalMatch fix = new AddAttributeOnLexicalMatch(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
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
		subHierarchy = gl.getConcept("64572001 |Disease (disorder)|");
		exclusions = new HashSet<>();

		//Order of terms is important since we'll search for the longest first to prevent
		//partial matches
		searchTermAttributeMap = new LinkedHashMap<>();
		/*DEVICES-109
		searchTermAttributeMap.put("non-sterile", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("863956004 |Non-sterile (qualifier value)|")));
		searchTermAttributeMap.put("sterile", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("261029002 |Sterile (qualifier value)|")));
		*/
		
		/*DEVICES-111
		searchTermAttributeMap.put("non-bioabsorbable", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("863965006 |Nonbioabsorbable (qualifier value)|")));
		searchTermAttributeMap.put("partially-bioabsorbable", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("863968008 |Partially bioabsorbable (qualifier value)|")));
		searchTermAttributeMap.put("non-absorbable", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("863967003 |Nonabsorbable (qualifier value)|")));
		searchTermAttributeMap.put("bioabsorbable", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("860574003 |Bioabsorbable (qualifier value)|")));
		searchTermAttributeMap.put("absorbable", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("863966007 |Absorbable (qualifier value)")));
		*/
		
		/*DEVICES-115
		searchTermAttributeMap.put("custom-made", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("860573009 |Custom-made (qualifier value)| ")));
		*/
		
		/*DEVICES-12 
		searchTermAttributeMap.put("uncoated", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("860575002 |Not coated with material (qualifier value)|")));
		searchTermAttributeMap.put("coated", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("866168000 |Coated with material (qualifier value)|")));
		searchTermAttributeMap.put("-on-", new RelationshipTemplate(HAS_DEVICE_CHARAC, gl.getConcept("866168000 |Coated with material (qualifier value)|")));
		
		//DEVICES-139
		searchTermAttributeMap.put("metal", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("425620007 |Metal (substance)|")));
		searchTermAttributeMap.put("metallic", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("425620007 |Metal (substance)|")));
		
		//DEVICES-140
		searchTermAttributeMap.put("synthetic polymer", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("272167000 |Synthetic polymer (substance)|")));
		searchTermAttributeMap.put("synthetic polymeric", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("272167000 |Synthetic polymer (substance)|")));
		searchTermAttributeMap.put("polymer", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("412155002 |Polymer (substance)|")));
		searchTermAttributeMap.put("polymeric", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("412155002 |Polymer (substance)|")));
		
		//DEVICES-141
		searchTermAttributeMap.put("silicone-gel", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("860705001 |Silicone gel (substance)|")));
		searchTermAttributeMap.put("silicone", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("13652007 |Silicone (substance)|")));
		
		//DEVICES=142
		searchTermAttributeMap.put("polyurethane-foam", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("878817006 |Polyurethane foam (substance)|")));
		searchTermAttributeMap.put("polyurethane", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("876840000 |Polyurethane (substance)|")));
		
		//DEVICES=143
		searchTermAttributeMap.put("ceramic", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("261253002 |Ceramic (substance)|")));
		
		//DEVICES=150
		searchTermAttributeMap.put("plastic", new RelationshipTemplate(HAS_COMP_MATERIAL, gl.getConcept("61088005 |Plastic (substance)|")));
		*/
		
		searchTermAttributeMap.put("idiopathic", new RelationshipTemplate(IS_A, gl.getConcept("41969006 |Idiopathic disease (disorder)|")));
		acceptableAlternatives = gl.getConcept("41969006").getDescendants(NOT_SET);
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
		int changesMade = NO_CHANGES_MADE;
		String fsn = " " + c.getFsn().toLowerCase();
		if (isExcluded(fsn)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept excluded due to lexical rule");
		} else {
			for (Map.Entry<String, RelationshipTemplate> entry : searchTermAttributeMap.entrySet()) {
				String searchTerm = entry.getKey();
				changesMade += checkAndReplace(t, c, fsn, searchTerm, entry.getValue());
				if (entry.getKey().contains("-")) {
					changesMade += checkAndReplace(t, c, fsn, searchTerm.replaceAll("-", " "), entry.getValue());
					changesMade += checkAndReplace(t, c, fsn, searchTerm.replaceAll("-", ""), entry.getValue());
				}
				//We can't check for changes because the attribute might already be present.
				//Check for the term again and if found, don't move on to the next search term
				if (fsn.contains(searchTerm) ||
						fsn.contains(searchTerm.replaceAll("-", " ")) ||
						fsn.contains(searchTerm.replaceAll("-", ""))){
					break;
				}
			}
		}
		return changesMade;
	}

	private int checkAndReplace(Task t, Concept c, String fsn, String searchTerm, RelationshipTemplate rel) throws TermServerScriptException {
		if (fsn.contains(searchTerm)) {
			Relationship attrib = rel.createRelationship(c, UNGROUPED, null);
			return addRelationship(t, c, attrib);
		}
		return NO_CHANGES_MADE;
	}

	private boolean isExcluded(String fsn) {
		for (String exclusionWord : exclusions) {
			if (fsn.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		for (Concept c : subHierarchy.getDescendants(NOT_SET)) {
			for (String searchTerm : searchTermAttributeMap.keySet()) {
				if (c.getFsn().toLowerCase().contains(searchTerm) //|| 
						//c.getFsn().toLowerCase().contains(searchTerm.replaceAll("-", " ")) ||
						//c.getFsn().toLowerCase().contains(searchTerm.replaceAll("-", ""))
						) {
					//Does the concept already feature the attribute being added?
					Set<Relationship> existing = c.getRelationships(searchTermAttributeMap.get(searchTerm), ActiveState.ACTIVE);
					if (existing.size() > 0) {
						report((Task)null, c, Severity.LOW, ReportActionType.NO_CHANGE, "Relationship already present", existing.iterator().next());
					} else {
						if (ensureNoAlternativesPresent(c)) {
							processMe.add(c);
						}
					}
					break;
				}
			}
		}
		return processMe;
	}

	private boolean ensureNoAlternativesPresent(Concept c) throws TermServerScriptException {
		for (Concept target : acceptableAlternatives) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getTarget().equals(target)) {
					report((Task)null, c, Severity.LOW, ReportActionType.NO_CHANGE, "More specific relationship already present ", target);
					return false;
				}
			}
		}
		return true;
	}
}
