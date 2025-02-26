package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class INFRA13580_UICCCopyAJCC extends DeltaGenerator implements ScriptConstants{

	private static final Map<String, String> REPLACEMENT_MAP = Map.of(
			"American Joint Committee on Cancer", "Union for International Cancer Control",
			"AJCC", "UICC"
	);

	private static final String BRAKETED_TEXT_REGEX = "\\([^\\)]*\\)";

	private Concept annotationType = null;
	protected Concept rootConcept;
	protected Concept newRootConcept;
	protected Map<Concept, Concept> newParentMap;

	protected Concept origPathologicalStageGroup;
	protected Concept origClinicalStageGroup;
	protected Concept origPathologicalGrade;
	protected Concept origClinicalGrade;

	protected Concept rpMCategory;
	protected Concept rcMCategory;
	protected Concept ypStageGroup;
	protected Concept ypGrade;

	public static void main(String[] args) throws TermServerScriptException {
		INFRA13580_UICCCopyAJCC delta = new INFRA13580_UICCCopyAJCC();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.init(args);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			Batch batch = delta.preProcess();
			delta.process(batch);
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Info, detail, , ",
				"SCTID, FSN, SemTag, Reason, detail, detail,"
		};

		String[] tabNames = new String[]{
				"Records Processed",
				"Records Skipped"
		};
		super.postInit(tabNames, columnHeadings, false);

		//Concept not promoted yet, so create a temporary copy
		newRootConcept = new Concept("1352503003", "Union for International Cancer Control allowable value (qualifier value)");
		annotationType = gl.getConcept("1295448001"); // |Attribution (attribute)|
		rootConcept = gl.getConcept("1222584008 |American Joint Committee on Cancer allowable value (qualifier value)|");

		//These groupers are additional to the obvious hierarchy, so we need to create and track them separately and add the counterparts as parents where appropriate
		origPathologicalStageGroup = gl.getConcept("1222593009 |American Joint Committee on Cancer pathological stage group allowable value (qualifier value)|");
		origClinicalStageGroup = gl.getConcept("1222592004 |American Joint Committee on Cancer clinical stage group allowable value|");
		origPathologicalGrade = gl.getConcept("1222599008 |American Joint Committee on Cancer pathological grade allowable value|");
		origClinicalGrade = gl.getConcept("1222598000 |American Joint Committee on Cancer clinical grade allowable value|");
		rpMCategory = gl.getConcept("1281795007 |American Joint Committee on Cancer rpM category allowable value|");
		rcMCategory = gl.getConcept("1281794006 |American Joint Committee on Cancer rcM category allowable value|");
		ypStageGroup = gl.getConcept("1222594003 |American Joint Committee on Cancer yp stage group allowable value|");
		ypGrade = gl.getConcept("1222600006 |American Joint Committee on Cancer yp grade allowable value|");

		newParentMap = Map.of(
				rootConcept, newRootConcept,
				origPathologicalStageGroup, new Concept("1352504009", "Union for International Cancer Control pathological stage group allowable value (qualifier value)"),
				origClinicalStageGroup, new Concept("1352505005", "Union for International Cancer Control clinical stage group allowable value (qualifier value)"),
				rpMCategory, new Concept("1352506006", "Union for International Cancer Control rpM category allowable value (qualifier value)"),
				origPathologicalGrade,new Concept("1352507002", "Union for International Cancer Control pathological grade allowable value (qualifier value)"),
				origClinicalGrade, new Concept("1352508007","Union for International Cancer Control clinical grade allowable value (qualifier value)")
		);
	}

	private Batch preProcess() throws TermServerScriptException {
		Batch batch = new Batch("INFRA-13580 UICC Clone concepts");
		List<Concept> acceptableSourceConcepts = processFile().stream().map(c -> (Concept)c).toList();
		//Now start with our parent concept and create a task for each of its immediate children
		for (Concept categoryConcept : rootConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Don't process rpM category, include it with rcM as they're cross wired
			if (acceptableSourceConcepts.contains(categoryConcept)
					&& !categoryConcept.equals(rpMCategory)) {
				Task t = batch.addNewTask(null);
				t.add(categoryConcept);
				addDescendantsAndStragglers(categoryConcept, t, acceptableSourceConcepts);
			}
		}
		return batch;
	}

	private void addDescendantsAndStragglers(Concept categoryConcept, Task t, List<Concept> acceptableSourceConcepts) throws TermServerScriptException {
		//And add all the descendant concepts in this task
		for (Concept c : categoryConcept.getDescendants(NOT_SET)) {
			if (acceptableSourceConcepts.contains(categoryConcept)) {
				t.add(c);
			}
		}

		//If this is the yp grade, add any stragglers from clinical + pathological grade
		//that aren't being picked up in any other category
		if (categoryConcept.equals(ypGrade)) {
			addStragglers(origClinicalGrade, t, acceptableSourceConcepts);
			addStragglers(origPathologicalGrade, t, acceptableSourceConcepts);
		}

		//If this is the yp stage group, add any stragglers from clinical + pathological stage
		//that aren't being picked up in any other category
		if (categoryConcept.equals(ypStageGroup)) {
			addStragglers(origClinicalStageGroup, t, acceptableSourceConcepts);
			addStragglers(origPathologicalStageGroup, t, acceptableSourceConcepts);
		}
	}

	private void addStragglers(Concept category, Task t, List<Concept> acceptableSourceConcepts) throws TermServerScriptException {
		//Are we missing any stragglers from this category in our task?
		for (Concept c : category.getDescendants(NOT_SET)) {
			if (acceptableSourceConcepts.contains(c) && !t.contains(c)) {
				t.add(c);
			}
		}
	}

	private void process(Batch batch) throws TermServerScriptException {
		for (Task t : batch.getTasks()) {
			//The parent of the first task will be our new root clone, and then after that
			Concept parent = newRootConcept;
			for (Component c : t.getComponents()) {
				Concept clone = cloneConceptAsUICC((Concept)c, parent);
				//Is this the first concept in the task?  It's then the parent for all subsequent concepts
				if (parent.equals(newRootConcept)) {
					parent = clone;
				}
			}
			if (!dryRun) {
				createOutputArchive(false, t.size());
				outputDirName = "output"; //Reset so we don't end up with _1_1_1
				initialiseOutputDirectory();
				initialiseFileHeaders();
			}
			gl.setAllComponentsClean();
		}
	}

	protected Concept cloneConceptAsUICC(Concept c, Concept parent) throws TermServerScriptException {
		Concept uicc = c.clone(conIdGenerator.getSCTID());
		for (Description d : new ArrayList<>(uicc.getDescriptions())) {
			normaliseDescription(uicc, d);
		}

		//Replace the parent concept
		for (Relationship parentRel : uicc.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE)) {
			//See if this is one of our additional parents that we have a known map for, and if not, use the assigned parent
			Concept newParent = newParentMap.getOrDefault(parentRel.getTarget(), parent);
			parentRel.setTarget(newParent);
			report(uicc, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "new Parent: " + newParent);
		}

		//Mark inferred relationships as clean so we don't export them
		for (Relationship r : uicc.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			r.setClean();
		}
		addAnnotation(uicc);
		outputRF2(uicc);
		report(c, Severity.LOW, ReportActionType.CONCEPT_ADDED, uicc);
		return uicc;
	}

	private void normaliseDescription(Concept c, Description d) throws TermServerScriptException {
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			c.removeDescription(d);
			return;
		}
		String originalTerm = d.getTerm();
		String term = d.getTerm();
		for (Map.Entry<String, String> replacement : REPLACEMENT_MAP.entrySet()) {
			term = term.replace(replacement.getKey(), replacement.getValue());
		}
		String textSansBrackets = term.replaceAll(BRAKETED_TEXT_REGEX, "");
		if (textSansBrackets.length() < 10) {
			term += " (UICC)";
		}
		d.setTerm(term);
		//We've added capital letters, so set the case sigificance to CS unless we started with a
		//number or a Roman numeral
		d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		if (Character.isDigit(originalTerm.charAt(0))) {
			d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		}
		if (d.getType().equals(DescriptionType.FSN)) {
			c.setFsn(term);
		}
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, originalTerm, d);
		String descId = descIdGenerator.getSCTID();
		d.setDescriptionId(descId);
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			l.setReferencedComponentId(descId);
		}
	}

	protected void addAnnotation(Concept c) throws TermServerScriptException {
		String annotationStr = "Union for International Cancer Control: https://www.uicc.org/who-we-are/about-uicc/uicc-and-tnm";
		ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(c, annotationType, annotationStr);
		c.addComponentAnnotationEntry(cae);
		report(c, Severity.LOW, ReportActionType.ANNOTATION_ADDED, annotationStr);
	}

}
