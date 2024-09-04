package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 */
public class QI1279_Reterm_RAST extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI1279_Reterm_RAST.class);

	private Map<String, Concept> substancesByPT;

	private Set<String> lexicalExclusions = new HashSet<>(Arrays.asList("radioallergosorbent"));

	private List<String> problematicConcepts = Arrays.asList(new String[] {
			"392503004","392539005","392462005","388550001","388576001","388581005","397728001","391477004","388607001",
			"397639009","388652003","414370009","397650007","397673004","414445005","392361004","388460007","399795002",
			"399815008","397649007","397674005","399838006","392370001","392372009","392436001","397667000","392382005",
			"388757001","392515000","392548000","392524009","392525005","392526006","392527002","392528007","388480008",
			"399804003","392388009","397646000","415865000"
	});

	protected QI1279_Reterm_RAST(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		QI1279_Reterm_RAST fix = new QI1279_Reterm_RAST(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.maxFailures = 9999;
			fix.groupByIssue = true;
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
		substancesByPT = SUBSTANCE.getDescendants(NOT_SET).stream()
				.collect(Collectors.toMap(c -> c.getPreferredSynonym(), c -> c));
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
		Set<Description> originalDescriptions = SnomedUtils.getDescriptionsList(c, ActiveState.BOTH, false);
		boolean hasUsGbVariance = SnomedUtils.hasUsGbPtVariance(c);
		if (hasUsGbVariance) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept has US/GB variance");
		}

		//Inactivate any descriptions that contain the word RAST
		boolean ptProcessed = false;
		for (Description d : originalDescriptions) {
			if (d.isActive() && d.getTerm().contains("RAST")) {
				d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
				d.setActive(false);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d);
				changesMade++;

				//Now, was this a PT?  Only process the first PT.  We'll pick up any gb/us variance now
				if (!ptProcessed && d.isPreferred() && d.getType().equals(DescriptionType.SYNONYM)) {
					ptProcessed = true;
					//We might have multiple substance names if there's US/GB Variance
					List<Description> substanceDescs = findSubstanceDescs(t, c);
					if (hasUsGbVariance && substanceDescs.size() < 2) {
						throw new TermServerScriptException("Concept indicates US/GB variance, but substance does not");
					} else if (!hasUsGbVariance && substanceDescs.size() > 1) {
						throw new TermServerScriptException("Concept indicates no US/GB variance, but substance has variance");
					}

					for (Description substanceDesc : substanceDescs) {
						String substanceName = substanceDesc.getTerm();
						report(t, c, Severity.NONE, ReportActionType.INFO, "Substance name(s) detected as: " + substanceName);
						//First try finding a description using X, the organism name
						substanceName = substanceName.replace("immunoglobulin ", "Ig");
						Description promotingDesc = findReplacementDescription(t, c, originalDescriptions, substanceName, false,false);

						if (promotingDesc == null) {
							//Can we find an "immunoglobulin" variant as template we can use to construct a new IgX?
							promotingDesc = findReplacementDescription(t, c, originalDescriptions, substanceName, false, true);
							//In this case we'll clone that description and tweak the text
							if (promotingDesc != null) {
								Description newPT = promotingDesc.clone(null);
								newPT.setTerm(newPT.getTerm().replace("immunoglobulin ", "Ig"));
								//SnomedUtils.upgradeTermToPreferred(newPT);
								//Set the acceptability to be the same as the substance name, to account for variances gb / us
								newPT.setAcceptabilityMap(substanceDesc.getAcceptabilityMap());
								c.addDescription(newPT);
								report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ADDED, newPT);
								changesMade++;
								break;
							}
						}

						if (promotingDesc == null) {
							//If that fails, try finding a description with some of the same words as the substance?
							promotingDesc = findReplacementDescription(t, c, originalDescriptions, substanceName,true, false);
						}

						if (promotingDesc == null) {
							//If that fails, try finding a description without X
							promotingDesc = findReplacementDescription(t, c, originalDescriptions, null, false,false);
							if (promotingDesc != null) {
								report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Promoting PT which did not begin with the substance name.  Please check", promotingDesc);
							}
						}

						if (promotingDesc == null) {
							String msg = "Unable to find replacement PT from: " + SnomedUtils.getDescriptions(c, false);
							throw new TermServerScriptException(msg);
						} else {
							int promotions = SnomedUtils.upgradeTermToPreferred(promotingDesc);
							changesMade += promotions;
							if (promotions > 0) {
								report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Promoted PT: " + promotingDesc);
							} else {
								report(t, c, Severity.HIGH, ReportActionType.NO_CHANGE, "PT already promoted for substance: " + substanceName, promotingDesc);
							}
						}
					}
				} else if (d.getType().equals(DescriptionType.FSN)) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN contains RAST");
				}
			}
		}
		report(t, c, Severity.LOW, ReportActionType.INFO, "Post update descriptions:", SnomedUtils.getDescriptions(c, false));
		//Have we lost our en/gb variance in this?
		if (hasUsGbVariance && !SnomedUtils.hasUsGbPtVariance(c)) {
			report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Concept lost US/GB variance during processing");
		}
		return changesMade;
	}

	private Description findReplacementDescription(Task t, Concept c, Set<Description> descs, String substanceName,boolean allowPartialMatch, boolean lastResort) throws TermServerScriptException {
		String targetPhrase = lastResort ? "specific immunoglobulin" : "specific Ig";
		//First try for an active description, 2nd pass will be inactive
		for (int i=0; i<2; i++) {
			for (Description d : descs) {
				if ((i == 0 && !d.isActive()) || (i == 1 && d.isActive())) {
					continue;
				}
				if (substanceName != null && !d.getTerm().startsWith(substanceName)) {
					continue;
				}
				if (d.getTerm().contains(targetPhrase)) {
					return d;
				}
			}
		}

		if (substanceName != null && allowPartialMatch) {
			Map<Description, Integer> scores = new HashMap<>();
			List<String> ignoreWords = Arrays.asList("specific", "immunoglobulin", "IgE", "IgG", "IgM", "IgA", "IgD", "Ig");
			List<String> partialMatch = Arrays.stream(substanceName.split(" "))
					.filter(s -> s.length() > 2)
					.filter(s -> !ignoreWords.contains(s))
					.toList();
			//Will we allow for a partial match on the substance name?  Active Descs only
			for (Description d : descs) {
				if (!d.isActive() || !d.getTerm().contains(targetPhrase)) {
					continue;
				}
				for (String word : partialMatch) {
					if (d.getTerm().contains(word)) {
						scores.put(d, scores.getOrDefault(d, 0) + 1);
					}
				}
			}
			if (scores.size() > 0) {
				int maxScore = Collections.max(scores.values());
				List<Description> bestMatches = scores.entrySet().stream()
						.filter(e -> e.getValue() == maxScore)
						.map(e -> e.getKey())
						.collect(Collectors.toList());
				if (bestMatches.size() == 1) {
					return bestMatches.get(0);
				} else if (bestMatches.size() > 1) {
					String msg = bestMatches.stream().map(d -> d.getTerm()).collect(Collectors.joining(", "));
					throw new TermServerScriptException("Multiple best matches found for " + substanceName + " : " + msg);
				}
			}
		}
		return null;
	}

	private List<Description> findSubstanceDescs(Task t, Concept c) throws TermServerScriptException {
		List<Description> substanceDescs = null;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (substancesByPT.values().contains(r.getTarget())) {
				if (substanceDescs != null) {
					throw new TermServerScriptException("Concept features multiple substances: " + getSubstancesStr(c));
				}
				substanceDescs = gl.getConcept(r.getTarget().getId()).getPreferredSynonyms().stream()
						.toList();
			}
		}
		return substanceDescs;
	}

	private String getSubstancesStr(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)
				.stream()
				.map( r -> gl.getConceptSafely(r.getTarget().getConceptId()))
				.filter(t -> substancesByPT.values().contains(t))
				.map(t -> t.getPreferredSynonym())
				.collect(Collectors.joining(",\n"));
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();

		nextConcept:
		for (Concept c : SnomedUtils.sort(findConcepts("<104380004 |Allergen specific antibody measurement (procedure)|"))) {
		//for (Concept c : findConcepts("388576001")){
			boolean targetWordFound = false;
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().contains("RAST")) {
					targetWordFound = true;
				}
				for (String exclusion : lexicalExclusions) {
					if (normalise(d).contains(exclusion)) {
						report(SECONDARY_REPORT, c, "Excluded by lexical exclusion: " + exclusion);
						continue nextConcept;
					}
				}
			}
			if (targetWordFound) {
				process.add(c);
				if (problematicConcepts.contains(c.getId())) {
					c.addIssue("Problematic concept");
				}
				continue nextConcept;
			}
		}
		return process;
	}



	private String normalise(Description d) {
		return " " + d.getTerm().toLowerCase() + " ";
	}
}
