package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Look through all LOINC expressions and fix whatever needs worked on
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LOINCStats extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(LOINCStats.class);

	private static String publishedRefsetFile = "G:\\My Drive\\018_Loinc\\2021\\der2_sscccRefset_LOINCExpressionAssociationSnapshot_INT_20170731.txt";
	//Alternatively in Unix /Volumes/GoogleDrive/My Drive/018_Loinc/2021/der2_sscccRefset_LOINCExpressionAssociationSnapshot_INT_20170731.txt
	
	private static final String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	
	private static final String ERROR = "ERROR";
	
	private enum RefsetCol { ID,EFFECTIVETIME,ACTIVE,MODULEID,REFSETID,REFERENCEDCOMPONENTID,MAPTARGET,EXPRESSION,DEFINITIONSTATUSID,CORRELATIONID,CONTENTORIGINID }
	
	private Map<String, List<String>> refsetFileMap;

	public static void main(String[] args) throws TermServerScriptException {
		LOINCStats app = new LOINCStats();
		try {
			ReportSheetManager.setTargetFolderId("1yF2g_YsNBepOukAu2vO0PICqJMAyURwh");  //LOINC
			app.getGraphLoader().setExcludedModules(new HashSet<>());
			app.init(args);
			app.loadProjectSnapshot(false);
			app.postInit();
			app.doReport();
			app.populateSummaryTab(PRIMARY_REPORT);
		} finally {
			app.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Item, Count",
				"Item, Detail, Further Info"
		};
		String[] tabNames = new String[] {
				"Summary Counts",
				"Details"
		};
		super.postInit(tabNames, columnHeadings);
		loadFiles();
	}

	public void doReport() throws TermServerScriptException {
		Map<String, Concept> currentContentMap =  gl.getAllConcepts().stream()
				.filter(c -> c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE))
				.collect(Collectors.toMap(c -> getLoincNumFromDescriptionSafely(c), c -> c));
		
		if (currentContentMap.containsKey(ERROR)) {
			LOGGER.error("One or more concepts did not specify a LOINCNum", (Exception)null);
		}
		
		//First work through the published map to see what's changed
		for (Map.Entry<String, List<String>> entry : refsetFileMap.entrySet()) {
			String loincNum = entry.getKey();
			String expression = entry.getValue().get(RefsetCol.EXPRESSION.ordinal());
			//Do we still have this item
			if (!currentContentMap.containsKey(loincNum)) {
				String issue = "Published LOINCNum no longer represented";
				incrementSummaryCount(issue);
				checkForInactiveLoincNumDescription(loincNum, issue);
			} else {
				Concept c = currentContentMap.get(loincNum);
				Set<Relationship> publishedRels = SnomedUtils.fromExpression(gl, expression);
				Set<Relationship> differences = findStatedRelDifferences(c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE), publishedRels);
				if (differences.size() > 0) {
					incrementSummaryCount("Published expressions with attribute changes");
					incrementSummaryCount("Published expression changes", differences.size());
				}
				differences = removeProperty(differences);
				if (differences.size() > 0) {
					incrementSummaryCount("Published expressions with attribute changes (ignore Property)");
					incrementSummaryCount("Published expression changes (ignore Property)", differences.size());
				}
			}
		}
		
		//Now work through all our current content
		for (Map.Entry<String, Concept> entry : currentContentMap.entrySet()) {
			String loincNum = entry.getKey();
			Concept c = entry.getValue();
			boolean isNew = false;
			if (c.isActive()) {
				if (!refsetFileMap.containsKey(loincNum)) {
					incrementSummaryCount("LoincNum new since Aug 2017");
					isNew = true;
				} else {
					//If this was present but now has null effective time, suggests that the definition status has been changed
					if (StringUtils.isEmpty(c.getEffectiveTime())) {
						incrementSummaryCount("Definition status updated (?)");
					}
				}
				
				if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
					incrementSummaryCount("Active concept P");
				} else {
					incrementSummaryCount("Active concept SD");
				}
			} else {
				incrementSummaryCount("Inactivated Expression");
				
				//Does the inactive concept have a historical association
				for (AssociationEntry e : c.getAssociationEntries(ActiveState.ACTIVE)) {
					Concept replacement = gl.getConcept(e.getTargetComponentId());
					if (currentContentMap.containsValue(replacement)) {
						incrementSummaryCount("Inactivated concept has replacement value");
					} else {
						incrementSummaryCount("Inactivated concept replacement value not found");
					}
				}
			}
			
			//Have any of the FSN/PT been modified since published?
			if (!isNew && hasDescriptionChanges(c)) {
				incrementSummaryCount("Has FSN/PT updated");
			}
		}
	}

	private Set<Relationship> removeProperty(Set<Relationship> rels) {
		Set<Relationship> filtered = new HashSet<>();
		for (Relationship r : rels) {
			Concept type = r.getType();
			if (!type.getConceptId().equals("704318007") && ! type.getConceptId().equals("370130000")) {
				filtered.add(r);
			}
		}
		return filtered;
	}

	private Set<Relationship> findStatedRelDifferences(Set<Relationship> lhsRels, Set<Relationship> rhsRels) {
		Set<Relationship> differences = new HashSet<>();
		for (Relationship lhs : lhsRels) {
			if (!isContained(lhs, rhsRels) && !isContainedLoose(lhs, differences)) {
				differences.add(lhs);
			}
		}
		
		for (Relationship rhs : rhsRels) {
			if (!isContained(rhs, lhsRels) && !isContainedLoose(rhs, differences)) {
				differences.add(rhs);
			}
		}
		return differences;
	}

	private boolean isContained(Relationship needle, Set<Relationship> haystack) {
		for (Relationship stalk : haystack) {
			if (stalk.getType().equals(needle.getType()) && stalk.getTarget().equals(needle.getTarget())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isContainedLoose(Relationship needle, Set<Relationship> haystack) {
		for (Relationship stalk : haystack) {
			if (stalk.getType().equals(needle.getType()) || stalk.getTarget().equals(needle.getTarget())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasDescriptionChanges(Concept c) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.isPreferred() && StringUtils.isEmpty(d.getEffectiveTime())) {
				return true;
			}
		}
		return false;
	}

	private void loadFiles() throws TermServerScriptException {
		refsetFileMap = new HashMap<>();
		try {
			//Load the Refset Expression file
			LOGGER.info("Loading {}",  publishedRefsetFile);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(publishedRefsetFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						List<String> items = Arrays.asList(line.split("\t"));
						String loincNum = items.get(RefsetCol.MAPTARGET.ordinal());
						refsetFileMap.put(loincNum, items);
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	
	private String getLoincNumFromDescription(Concept c) throws TermServerScriptException {
		return getLoincNumDescription(c, ActiveState.ACTIVE).getTerm().substring(LOINC_NUM_PREFIX.length());
	}
	
	private String getLoincNumFromDescriptionSafely(Concept c) {
		try {
			return getLoincNumFromDescription(c);
		} catch (Exception e) {
			LOGGER.error("Exception encountered",e);
		}
		return ERROR;
	}
	
	private String getLoincNumFromInactiveDescription(Concept c) {
		try {
			return getLoincNumDescription(c, ActiveState.INACTIVE).getTerm().substring(LOINC_NUM_PREFIX.length());
		} catch (Exception e) {}
		return ERROR;
	}
	
	private Description getLoincNumDescription(Concept c, ActiveState activeState) throws TermServerScriptException {
		for (Description d : c.getDescriptions(activeState)) {
			if (d.getTerm().startsWith(LOINC_NUM_PREFIX)) {
				return d;
			}
		}
		throw new TermServerScriptException(c + " does not specify a LOINC num");
	}

	private void checkForInactiveLoincNumDescription(String loincNum, String issue) throws TermServerScriptException {
		Concept foundInactiveDesc = gl.getAllConcepts().stream()
				.filter(c -> c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE))
				.filter(c -> getLoincNumFromInactiveDescription(c).equals(loincNum))
				.findAny().orElseGet( () -> { return null; });
		String extraDetail = foundInactiveDesc == null ? "" : "Found as inactive description on " + foundInactiveDesc;
		report(SECONDARY_REPORT, issue, loincNum, extraDetail);
	}
}
