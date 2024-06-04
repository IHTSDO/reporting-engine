package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.ImmutableSet;

/**
 */
public class INFRA10900_Reterm_Taxonomy extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA10900_Reterm_Taxonomy.class);

	public static Set<String> taxonomyNames = ImmutableSet.of(" clade ", " class ", 
			" division ", " domain ", " family ", " genus ", " infraclass ", " infraclass ", 
			" infrakingdom ", " infraorder ", " infraorder ", " kingdom ", " order ", 
			" phylum ", " species ", " subclass ", " subdivision ", " subfamily ", 
			" subgenus ", " subkingdom ", " suborder ", " subphylum ", " subspecies ", 
			" superclass ", " superdivision ", " superfamily ", " superkingdom ", 
			" superorder ", " superphylum ");
	public static Set<String> skipList = ImmutableSet.of( "Family history", "Family planning", "Family with", 
			"Family education", "Division of");
	public static Set<String> skipClass = ImmutableSet.of( "I", "II", "III", "IIb", "IV", "IVa", "V", "VI");
	private Set<Concept> organisms;
	private Set<Concept> bodyStructures;
	
	protected INFRA10900_Reterm_Taxonomy(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA10900_Reterm_Taxonomy fix = new INFRA10900_Reterm_Taxonomy(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail, Additional Detail";
			fix.init(args);
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, Severity, Action, Details, , , ",
				"SCTID, FSN, SemTag, Taxonomy, Issue, Expression, Details, , , "};
		String[] tabNames = new String[] {"Processed", "Skip but check"};
		super.postInit(tabNames, columnHeadings, false);
		organisms = ORGANISM.getDescendants(NOT_SET);
		bodyStructures = BODY_STRUCTURE.getDescendants(NOT_SET);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
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

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		Set<Description> originalDescriptions = SnomedUtils.getDescriptionsList(c, false);
		nextDescription:
		for (Description d : originalDescriptions) {
			for (String taxonomyName : taxonomyNames) {
				if (normalise(d).contains(taxonomyName)) {
					boolean startsWith = normalise(d).startsWith(taxonomyName);
					String organismName = findOrganismName(t, c);
					String replacement = d.getTerm().replace(taxonomyName.trim(), "")
							.replace(StringUtils.capitalizeFirstLetter(taxonomyName.trim()), "");
					
					replacement = replacement.trim().replaceAll("  ", " ");
					
					if (startsWith) {
						replacement = StringUtils.capitalizeFirstLetter(replacement);
					} else {
						replacement = replacement.replace(organismName.toLowerCase(), organismName);
					}
					
					CaseSignificance cs = d.getCaseSignificance();
					if (cs.equals(CaseSignificance.CASE_INSENSITIVE) ||
							cs.equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
						if (replacement.startsWith(organismName)) {
							cs = CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
						} else {
							cs = CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
						}
						changesMade++;
					}
					
					Description newDescription = replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, "", cs);
					changesMade++;

					continue nextDescription;
				}
			}
		}
		return changesMade;
	}

	private String findOrganismName(Task t, Concept c) throws TermServerScriptException {
		String organismName = null;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (organisms.contains(r.getTarget())) {
				if (organismName != null) {
					report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept features multiple organisms");
				}
				organismName = gl.getConcept(r.getTarget().getId()).getPreferredSynonym();
				for (String taxonomyName : taxonomyNames) {
					organismName = organismName.replace(StringUtils.capitalizeFirstLetter(taxonomyName.trim()), "");
				}
				organismName = organismName.trim().replaceAll("  ", " ");
			}
		}
		return organismName;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		nextConcept:
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.getId().equals("64921000119105")) {
				debug("here");
			}
			//Organisms can be ignored.
			if (!c.isActive() || organisms.contains(c) || bodyStructures.contains(c)) {
				continue;
			}
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
					continue;
				}
				
				for (String skip : skipList) {
					if (d.getTerm().startsWith(skip)) {
						continue nextConcept;
					}
				}
				//if (d.isPreferred()) {
				for (String taxonomyName : taxonomyNames) {
					if (normalise(d).contains(taxonomyName)) {
						//Is it a class?   Watch out for class ii etc
						if (hasClassSkip(d.getTerm())) {
							report(SECONDARY_REPORT, c, taxonomyName, "Has class skip", SnomedUtils.getDescriptions(c, false), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
							continue nextConcept;
						}
						//Yes but do we have an organism for an attribute value?
						if (hasAttributeValue(c, organisms)) {
								process.add(c);
						} else {
							report(SECONDARY_REPORT, c, taxonomyName, "Does not feature Organism attribute", SnomedUtils.getDescriptions(c, false), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
						}
						continue nextConcept;
					}
				}
				//}
			}
		}
		return process;
	}

	private boolean hasClassSkip(String term) {
		String[] words = term.split(" ");
		for (int i=0; i < words.length - 1; i++) {
			if (words[i].equals("class")) {
				for (String skipWord : skipClass) {
					if (words[i+1].equals(skipWord)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean hasAttributeValue(Concept c, Set<Concept> targetValues) {
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (targetValues.contains(r.getTarget())) {
				return true;
			}
		}
		return false;
	}

	private String normalise(Description d) {
		return " " + d.getTerm().toLowerCase() + " ";
	}
}
