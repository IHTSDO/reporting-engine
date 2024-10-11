package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-1209
 */
public class QI1209_ReplaceMeasurementAttributeAndReterm extends BatchFix {

	private Map<String,String> textReplacementMap;
	private RelationshipTemplate matchTemplate;
	private RelationshipTemplate replaceTemplate;
	
	private Set<String> textExclusions;
	
	protected QI1209_ReplaceMeasurementAttributeAndReterm(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		QI1209_ReplaceMeasurementAttributeAndReterm fix = new QI1209_ReplaceMeasurementAttributeAndReterm(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Task Key, Task Description, Concept SCTID,FSN, ,Severity, Action, Details, Details, Details, , ,",
				"Concept SCTID,FSN,Descriptions,Expression"};
		String[] tabNames = new String[] {
				"Processed",
				"Excluded"};
		super.postInit(tabNames, columnHeadings, false);
		
		textExclusions = new HashSet<>();
		textExclusions.add("false");
		
		subsetECL = " << 118245000 |Measurement finding (finding)|";
		
		textReplacementMap = new HashMap<>();
		//textReplacementMap.put("positive", "detected");
		textReplacementMap.put("negative", "not detected");
		
		/*matchTemplate = new RelationshipTemplate(HAS_INTERPT, 
				gl.getConcept("10828004 |Positive (qualifier value)|"));
		replaceTemplate = new RelationshipTemplate(HAS_INTERPT, 
				gl.getConcept("260373001 |Detected (qualifier value)|"));*/
		
		matchTemplate = new RelationshipTemplate(HAS_INTERPRETATION,
				gl.getConcept("260385009 |Negative (qualifier value)|"));
		replaceTemplate = new RelationshipTemplate(HAS_INTERPRETATION,
				gl.getConcept("260415000 |Not detected (qualifier value)|"));
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyConceptIfRequired(task, loadedConcept);
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

	private int modifyConceptIfRequired(Task t, Concept c) throws TermServerScriptException {
		/*if (c.getId().equals("165816005")) {
			debug("Here");
		}*/
		int changesMade = modifyDescriptions(t, c);
		changesMade += replaceAttribute(t, c);
		return changesMade;
	}

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		SnomedUtils.prioritise(originalDescriptions);
		for (Description d : originalDescriptions) {
			if (!d.isPreferred()) {
				//Only modifying FSN and PT
				continue;
			}
			for (Map.Entry<String, String> entry : textReplacementMap.entrySet()) {
				String find = entry.getKey();
				String replace = entry.getValue();
				String termLower = d.getTerm().toLowerCase();
				if (termLower.contains(find)) {
					String newTerm = d.getTerm().replaceAll("(?i)"+find, replace);
					if (newTerm.startsWith(replace)) {
						newTerm = StringUtils.capitalize(newTerm);
					}
					if (!newTerm.equals(d.getTerm())) {
						replaceDescription(t, c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true);
						changesMade++;
					}
				}
			}
		}
		return changesMade;
	}

	private int replaceAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(matchTemplate, ActiveState.ACTIVE)) {
			if (replaceTemplate != null) {
				Relationship replaceAttrib = replaceTemplate.createRelationship(c, r.getGroupId(), null);
				changesMade += replaceRelationship(t, c, r, replaceAttrib);
			}
			
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		setQuiet(true);
		List<Component> componentsToProcess = SnomedUtils.sort(findConcepts(subsetECL))
				.stream()
				.filter(c -> !isExcluded(c))
				.filter(c -> modifyConceptIfRequiredSafely(c) > 0)
				.collect(Collectors.toList());
		setQuiet(false);
		return componentsToProcess;
	}

	private boolean isExcluded(Concept c) {
		try {
			for (String textExclusion : textExclusions) {
				if (c.getFsn().toLowerCase().contains(textExclusion)) {
					String descriptions = SnomedUtils.getDescriptions(c);  //Builds prioritised list
					report(SECONDARY_REPORT, c.getId(), c.getFsn(), descriptions, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
					return true;
				}
			}
			return false;
		} catch (TermServerScriptException e) {
			throw new IllegalStateException(e);
		}
	}

	private int modifyConceptIfRequiredSafely(Concept c) {
		try {
			//We'll do a cheeky filter out of Disease concepts here, and report on a 2nd tab
			if (c.getFsn().contains("(disorder)")) {
				// ... but only if they're relevant to the changes being made generally
				if (modifyConceptIfRequired(null, c.cloneWithIds()) > 0) {
					String descriptions = SnomedUtils.getDescriptions(c);  //Builds prioritised list
					report(SECONDARY_REPORT, c.getId(), c.getFsn(), descriptions, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				}
				return NO_CHANGES_MADE;
			}
			return modifyConceptIfRequired(null, c.cloneWithIds());
		} catch (TermServerScriptException e) {
			throw new IllegalStateException(e);
		}
	}
}
