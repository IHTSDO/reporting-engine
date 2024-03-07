package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 */
public class RetermConcepts extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(RetermConcepts.class);
	
	private String identifyingText = "angioplasty";
	private String excludeText = null;
	private Map<String, String> replacementMap;
	private String prefix = "Transluminal";
	private boolean forcePTAlignment = false;
	private Collection<Concept> requiredAttributeValue = null;
	Map<String, Concept> currentFSNs = new HashMap<>();
	List<DescriptionType> typesOfInterest = List.of(DescriptionType.FSN);
	
	protected RetermConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RetermConcepts fix = new RetermConcepts(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		subsetECL = "<< 418285008 |Angioplasty of blood vessel (procedure)|";
		replacementMap = new HashMap<>();
		//text should be lower case.
		//TODO Make replacement match case, if there's a chance of it being the first word
		//replacementMap.put("bladder", "urinary bladder");
		
		//requiredAttributeValue = findConcepts("<< 89837001|Urinary bladder structure|");

		LOGGER.info("Obtaining all current FSNs...");
		//There are duplicates.  In the case of duplicate, we'll keep the first one we find.
		gl.getAllConcepts().stream()
				.collect(Collectors.toMap(Concept::getFsn, Function.identity(), (existing, replacement) -> existing));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = reterm(task, loadedConcept);
			if (changesMade > 0 && forcePTAlignment) {
				changesMade += forcePTAlignment(task, loadedConcept);
			}
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

	private int reterm(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		//for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
		//	String find = entry.getKey();
		//	String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, typesOfInterest)) {
				//Skip FSNs & text definitions
				/*if (!d.getType().equals(DescriptionType.SYNONYM)) {
					continue;
				}*/
				//Only process FSN + PT
				//Only process FSN
				if (!d.getType().equals(DescriptionType.FSN)/*!d.isPreferred()*/ || d.getTerm().toLowerCase().contains(prefix)) {
					continue;
				}
				
				//
				//In this case we're looking for an entire match
				/*if ((d.getTerm().contains(find) || d.getTerm().contains(StringUtils.capitalizeFirstLetter(find)))
						&& !d.getTerm().contains(excludeText)) {
					if (!d.isReleased()) {
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
					}
					String replacement = d.getTerm().replaceAll(find, replace);*/
				if (d.getTerm().toLowerCase().contains(identifyingText)) {
					String replacement = null;
					boolean normalizePT = true;
					if (d.getTerm().toLowerCase().startsWith(identifyingText)) {
						replacement = prefix + " " + StringUtils.decapitalizeFirstLetter(d.getTerm());
					} else {
						replacement = d.getTerm().replaceAll(identifyingText, prefix.toLowerCase() + " " + identifyingText);
						normalizePT = false;
					}


					//If the term is unchanged, try an upper case replacement
					//if (replacement.equals(d.getTerm())) {
					//	replacement = d.getTerm().replaceAll(StringUtils.capitalizeFirstLetter(find), StringUtils.capitalizeFirstLetter(replace));
					//}

					//If the replacement is a known FSN, we'll skip
					if (currentFSNs.containsKey(replacement)) {
						report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement is a known FSN: " + replacement);
						continue;
					}

					//And we'll replace the PT while we're about it, but warn if there's GB variance
					String ptTerm = SnomedUtils.deconstructFSN(replacement)[0];
					Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
					Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);

					//If the PT does not start with our identifying text, we'll leave it alone
					if (!usPT.getTerm().toLowerCase().startsWith(identifyingText)) {
						normalizePT = false;
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "PT not normalized as FSN is not pattern 'Angioplasty of X'", usPT);
						String ptReplacement = usPT.getTerm().replace(identifyingText, prefix.toLowerCase() + " " + identifyingText);
						replaceDescription(t, c, usPT, ptReplacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true, null);
					}

					replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, normalizePT, "", null);

					if (normalizePT) {
						forcePTAlignment(t, c);
					}

					if (!usPT.equals(gbPT)) {
						report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Check US / GB Variance", gbPT);
					}
					changesMade++;
				} else {
					report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "FSN did not start with " + identifyingText);
				}
		//	}
		}
		return changesMade;
	}
	
	
	private int forcePTAlignment(Task t, Concept c) throws TermServerScriptException {
		String alignedPT = SnomedUtils.deconstructFSN(c.getFsn())[0];
		Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
		
		if (!usPT.equals(gbPT)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "US/GB Variance!", gbPT);
		}
		
		//We've already deleted the unwanted terms, so we'll demote this PT rather than inactivate
		if (!alignedPT.equals(usPT.getTerm())) {
			replaceDescription(t, c, usPT, alignedPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true, null);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		nextConcept:
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL, true, true))) {
		//for (Concept c : Collections.singleton(gl.getConcept("710074006"))) {
			if (!containsRequiredAttributeValue(c)) {
				continue;
			}
			//Flag up any descriptions that have both the find AND the replace in the same term.
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getType().equals(DescriptionType.TEXT_DEFINITION) || !d.isPreferred()) {
					continue;
				}
				String termLower = d.getTerm().toLowerCase();
				if (termLower.contains(identifyingText) 
						&& (excludeText == null  || !termLower.contains(excludeText))
						&& !termLower.contains(prefix.toLowerCase())) {
					toProcess.add(c);
					break;
				}
				
			/*	for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
					String find = entry.getKey();
					String replace = entry.getValue();
					String term = d.getTerm().toLowerCase();
					if (term.contains(find) && term.contains(replace)) {
						report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Term contains both '" + find + "' and '" + replace + "'", d);
						continue nextConcept;
					}
				}
			}
			
			if (c.getFsn().toLowerCase().contains(identifyingText)) {
				for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
					String find = entry.getKey();
					for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
						//Skip FSNs & text definitions
						if (!d.getType().equals(DescriptionType.SYNONYM)) {
							continue;
						}
						//In this case we're looking for an entire match
						if (d.getTerm().contains(find)) {
							toProcess.add(c);
							continue nextConcept;
						}
					}
				}
			}*/
			}
		}
		return toProcess;
	}

	private boolean containsRequiredAttributeValue(Concept c) {
		if (requiredAttributeValue == null) {
			return true;
		}
		return c.getRelationships().stream()
				.filter(r -> r.isActive())
				.filter(r -> !r.getType().equals(IS_A))
				.filter(r -> !r.isConcrete())
				.map(r -> r.getTarget())
				.anyMatch(t -> requiredAttributeValue.contains(t));
	}
}
