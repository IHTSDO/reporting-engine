package org.ihtsdo.termserver.scripting.reports.managedService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.reports.release.HistoricDataUser;
import org.ihtsdo.termserver.scripting.reports.release.HistoricStatsGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * FRI-254 A number of what were originally SQL queries now converted into a user-runnable
 * report
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtensionImpactReport extends HistoricDataUser implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionImpactReport.class);

	private static String INTERNATIONAL_RELEASE = "Proposed International Release Archive";
	
	private String incomingDataKey;
	private Map<String, Datum> incomingData;
	
	private Map<Concept, Set<Concept>> usedInStatedModellingMap; 
	private Map<Concept, Set<Concept>> usedAsStatedParentMap;
	
	private String[][] columnNames;  //Used for both column names, and to track totals
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(INTERNATIONAL_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20231001T120000Z.zip");
		TermServerReport.run(ExtensionImpactReport.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INTERNATIONAL_RELEASE).withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Extension Impact Report")
				.withDescription("This report estimates the size of the changes that will need to be made when the extension is upgraded to the latest international edition.  Leave the parameter blank for a preview of impact of current unpublished MAIN.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		origProject = run.getProject();
		summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
		
		if (!getProject().getBranchPath().startsWith("MAIN/SNOMEDCT-")) {
			throw new TermServerScriptException("Cannot work with '" + run.getProject() + "'. This report can only be run against an extension. ");
		}
		
		if (!StringUtils.isEmpty(getJobRun().getTask())) {
			throw new TermServerScriptException("This report cannot be run against tasks");
		}
		//getArchiveManager(true).setRunIntegrityChecks(false);
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		boolean compareTwoSnapshots = false; 
		previousTransitiveClosureNeeded = false;
		LOGGER.info("International Release data being imported, wiping Graph Loader for safety.");
		getArchiveManager(true).reset(false);
		Project previousProject = project.clone();
		//boolean loadEditionArchive = false;
		
		if (StringUtils.isEmpty(getJobRun().getParamValue(INTERNATIONAL_RELEASE))) {
			Branch branch = tsClient.getBranch("MAIN");
			project.setBranchPath("MAIN");
			project.setKey("MAIN");
			project.setMetadata(branch.getMetadata());
		} else {
			projectKey = getJobRun().getParamValue(INTERNATIONAL_RELEASE);
			project.setKey(projectKey);
			project.setBranchPath(projectKey);
			//loadEditionArchive = true;
		}

		try {
			incomingDataKey = project.getKey();
			ArchiveManager mgr = getArchiveManager(true);
			//mgr.setLoadEditionArchive(loadEditionArchive);
			mgr.loadSnapshot(fsnOnly);
			
			HistoricStatsGenerator statsGenerator = new HistoricStatsGenerator(this);
			statsGenerator.runJob();
			mgr.reset();
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Historic Data Generation failed due to " + e.getMessage(), e);
		}
		projectKey = previousProject.getKey();
		project = previousProject;
		loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	};

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		thisDependency = project.getMetadata().getDependencyPackage();
		LOGGER.info("Setting dependency archive: " + thisDependency);
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(false, fsnOnly);
	}

	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}
		
		columnNames = new String[][] {	{"Has Inactivated Stated Parent", "Inactivated Concept Used As Stated Parent", "Has Inactivated Stated Attribute", "Inactivated Concept Used In Stated Modelling", "Has Inactivated Inferred Parent", "Inactivated Concept Used As Inferred Parent"},
										{"New Concept Requires Translation", "Updated FSN Requires Translation", "Updated FSN No Current Translation", "Translated Concept Inactivated - Replacement Requires Translation"}};
		
		String[] columnHeadings = new String[] {"Summary Item, Count",
												"SCTID, FSN, SemTag," + formColumnNames(columnNames[0], true),
												"SCTID, FSN, SemTag," + formColumnNames(columnNames[1], false),
												"SCTID, FSN, SemTag,Impact,Affected Concept"};
		
		String[] tabNames = new String[]{"Summary Counts",  //PRIMARY
				"Inactivations",   //SECONDARY
				"Translations",   //TERTIARY
				"Inactivation Detail",  //QUATERNARY
		};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	private String formColumnNames(String[] columnNames, boolean includeExamples) {
		String header = "";
		boolean isFirst = true;
		int pairCount = 0;
		for (String columnName : columnNames) {
			if (!isFirst) {
				header += ",";
			} else {
				isFirst = false;
			}
			header += columnName;
			pairCount++;
			
			if (includeExamples && pairCount == 2) {
				header += ",Example";
				pairCount = 0;
			}
		}
		header += ", ,";
		return header;
	}

	public void runJob() throws TermServerScriptException {
		//We've always loaded historic data as 'prev' but in this case, we're looking the release that
		//we're about to upgrade to.  So we'll give that a more appropriate name
		LOGGER.info ("Loading Previous Data");
		incomingData = loadData(incomingDataKey); 
		
		LOGGER.info("Populating map of all concepts used in stated modelling");
		populateStatedModellingMap();
		
		//Executor executor = Executors.newFixedThreadPool(7);
		
		//Work through the top level hierarchies
		List<Concept> topLevelHierarchies = SnomedUtils.sort(ROOT_CONCEPT.getDescendents(IMMEDIATE_CHILD));
		for (Concept topLevelConcept : topLevelHierarchies) {
			LOGGER.info("Processing - " + topLevelConcept);
			Set<String> thisHierarchy = getHierarchy(topLevelConcept);
			
			reportInactivations(topLevelConcept, thisHierarchy, columnNames[0]);
			reportTranslations(topLevelConcept, thisHierarchy, columnNames[1]);
		}
		
		//We can now populate all the of the total columns
		writeTotalRow(SECONDARY_REPORT, columnNames[0], true);
		writeTotalRow(TERTIARY_REPORT, columnNames[1], false);
	}

	private void reportInactivations(Concept topLevelConcept, Set<String> thisHierarchy, String[] summaryNames) throws TermServerScriptException {
		LOGGER.info("Reporting Inactivations");
		int hasInactivatedStatedParent = 0; 
		int inactivatedConceptUsedAsStatedParent = 0;
		int hasInactivatedStatedAttribute = 0;
		int inactivatedConceptUsedInStatedModelling = 0;
		int hasInactivatedInferredParent = 0;
		int inactivatedConceptUsedAsInferredParent = 0;
		String[] examples = new String[3];
		
		Set<Concept> noInScopeDescendentsCache = new HashSet<>();
		Set<Concept> yesInScopeDescendentsCache = new HashSet<>();
		
		for (String sctId : thisHierarchy) {
			Concept currentConcept = gl.getConcept(sctId, false, false);  //Don't create or validate
			//If this concept is already inactive, or doesn't yet exist we don't need to count it
			if (currentConcept == null || !currentConcept.isActive()) {
				continue;
			}
			//Or if it's not in the International Edition or still active in the incoming data
			Datum datum = incomingData.get(sctId);
			if (datum == null || datum.isActive) {
				continue;
			}
			
			//But the IMPACT is not the count of International Concepts involved, 
			//but how many _extension_ concepts each inactivated INT concept is used in.
			
			Set<Concept> usedIn = usedAsStatedParentMap.get(currentConcept);
			if (usedIn != null && usedIn.size() > 0) {
				hasInactivatedStatedParent += usedIn.size();
				inactivatedConceptUsedAsStatedParent++;
				incrementSummaryInformation(summaryNames[0], usedIn.size());
				incrementSummaryInformation(summaryNames[1]);
				Concept exampleConcept = usedIn.iterator().next();
				examples[0] = currentConcept + "\nParent of : " + exampleConcept;
				for (Concept detail : usedIn) {
					report(QUATERNARY_REPORT, currentConcept, "Becomes Inactive Stated Parent Of", detail);
				}
			}
			
			usedIn = usedInStatedModellingMap.get(currentConcept);
			if (usedIn != null) {
				hasInactivatedStatedAttribute += usedIn.size();
				inactivatedConceptUsedInStatedModelling++;
				incrementSummaryInformation(summaryNames[2], usedIn.size());
				incrementSummaryInformation(summaryNames[3]);
				Concept exampleConcept = usedIn.iterator().next();
				examples[1] =  currentConcept + "\nUsed in : " + usedIn + "\n" + exampleConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				for (Concept detail : usedIn) {
					report(QUATERNARY_REPORT, currentConcept, "Becomes Inactive Used In Modelling Of", detail);
				}
			}
			
			//Does this concept have any inScope children?  We'll step this with new code
			//to short cut finding the complete set and allocating memory for that
			if (hasInScopeDescendents(currentConcept, noInScopeDescendentsCache, yesInScopeDescendentsCache)) {
				long countInferredChildren = currentConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).stream()
						.filter(child -> inScope(child))
						.count();
				if (countInferredChildren > 0) {
					hasInactivatedInferredParent++;
					inactivatedConceptUsedAsInferredParent += countInferredChildren;
					incrementSummaryInformation(summaryNames[4], (int)countInferredChildren);
					incrementSummaryInformation(summaryNames[5]);
					examples[2] = currentConcept + " inferred parent of " + currentConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).iterator().next();
				}
			}
		}
		report(SECONDARY_REPORT, topLevelConcept, hasInactivatedStatedParent, inactivatedConceptUsedAsStatedParent, examples[0], hasInactivatedStatedAttribute, inactivatedConceptUsedInStatedModelling, examples[1], hasInactivatedInferredParent, inactivatedConceptUsedAsInferredParent, examples[2]);
	}


	private void reportTranslations(Concept topLevelConcept, Set<String> thisHierarchy, String[] summaryNames) throws TermServerScriptException {
		LOGGER.info("Reporting Translations Required");
		int newConceptCount = 0;
		int changedFSNCount = 0;
		int changedFSNCountNoCurrent = 0;
		int translatedInactivatedCount = 0;
		Set<String> conceptReplacementSeen = new HashSet<>();
		
		for (String sctId : thisHierarchy) {
			Concept currentConcept = gl.getConcept(sctId, false, false);  //Don't create or validate
			//If this concept does not currently exist, then it's new, so it'll need a translation
			if (currentConcept == null) {
				//No need to check scope, this can only have come from the international edition
				newConceptCount++;
				incrementSummaryInformation(summaryNames[0]);
				continue;
			}
			
			//Now we don't need to worry about any changes to extension components - they've already been made
			if (!SnomedUtils.isInternational(currentConcept)) {
				continue;
			}
			
			Datum datum = incomingData.get(sctId);
			if (datum == null) {
				throw new TermServerScriptException(sctId + " is known to extension, is considered International and yet is not known to proposed update package.  Check date specified.  It must be in advance of current extension upgrade point");
			}
			
			//Has the FSN changed from what's currently here?
			if (!currentConcept.getFsn().equals(datum.fsn)) {
				if (hasTranslation(currentConcept)) {
					changedFSNCount++;
					incrementSummaryInformation(summaryNames[1]);
				} else {
					changedFSNCountNoCurrent++;
					incrementSummaryInformation(summaryNames[2]);
				}
			}
			
			//Report translated concepts that have been inactivated where the replacement has not been translated
			if (!datum.isActive && 
					currentConcept.isActive() &&
					hasTranslation(currentConcept)) {
				//Can't use association entries because of course _this_ snapshot doesn't know 
				//about the inactivation.  Pull it from the datum instead
				for (String histAssocTarget : datum.histAssocTargets) {
					//Only count a given replacement once
					if (!conceptReplacementSeen.contains(histAssocTarget)) {
						Concept targetConcept = gl.getConcept(histAssocTarget, false, false);
						//If we don't have this concept then we'll already have counted it
						if (targetConcept != null && !hasTranslation(targetConcept)) {
							translatedInactivatedCount++;
							incrementSummaryInformation(summaryNames[3]);
						}
						conceptReplacementSeen.add(histAssocTarget);
					}
				}
			}
		}
		
		report(TERTIARY_REPORT, topLevelConcept, newConceptCount, changedFSNCount, changedFSNCountNoCurrent, translatedInactivatedCount);
	}

	private void writeTotalRow(int tabNum, String[] columnNames, boolean includeExamples) throws TermServerScriptException {
		String[] data = new String[columnNames.length];
		if (includeExamples) {
			data = new String[columnNames.length + 3];
		}
		int idx = 0;
		for (String columnName : columnNames) {
			int count = getSummaryInformationInt(columnName);
			countIssue(null, count);
			data[idx++] = Integer.toString(count);
			if (includeExamples && idx > 0 && idx % 2 == 0) {
				data[idx++] = "N/A";
			}
		}
		report(tabNum, "");
		report(tabNum, "", "", "Total:", data);
	}

	private Set<String> getHierarchy(Concept topLevelConcept) throws TermServerScriptException {
		//Form the top level set as the sum of current hierarchy and what's in the incoming data
		Set<String> hierarchy = new HashSet<>();
		hierarchy = topLevelConcept.getDescendents(NOT_SET).stream()
				.map(c -> c.getId())
				.collect(Collectors.toSet());
		
		//Now add in what we see in the proposed upgrade version
		for (Map.Entry<String, Datum> entry : incomingData.entrySet()) {
			if (entry.getValue().hierarchy != null
					&& entry.getValue().hierarchy.equals(topLevelConcept.getId())) {
				hierarchy.add(entry.getKey());
			}
		}
		return hierarchy;
	}

	private void populateStatedModellingMap() {
		usedInStatedModellingMap = new HashMap<>();
		usedAsStatedParentMap = new HashMap<>();
		//I'm not calling getRelationships(Active, Stated) because that causes memory to be allocated
		//and we're going through A LOT of loops here, so just test values
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive() && inScope(c)) {
				for (Relationship r : c.getRelationships()) {
					if (r.isActive() && r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP)) {
						if (r.getType().equals(IS_A)) {
								addToMap(usedAsStatedParentMap, r.getType(), c);
								addToMap(usedAsStatedParentMap, r.getTarget(), c);
						} else {
							addToMap(usedInStatedModellingMap, r.getType(), c);
							if (r.isNotConcrete()) {
								addToMap(usedInStatedModellingMap, r.getTarget(), c);
							}
						}
					}
				}
			}
		}
	}

	private void addToMap(Map<Concept, Set<Concept>> map, Concept key, Concept value) {
		Set<Concept> set = map.get(key);
		if (set == null) {
			set = new HashSet<>();
			map.put(key, set);
		}
		set.add(value);
	}

	private boolean hasInScopeDescendents(Concept c, Set<Concept> noInScopeDescendentsCache, Set<Concept> yesInScopeDescendentsCache) {
		for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//If we already know that this concept has no descendants, then no need to check again
			if (noInScopeDescendentsCache.contains(child)) {
				return false;
			}
			
			if (yesInScopeDescendentsCache.contains(child)) {
				return true;
			}
			
			if (inScope(child) || hasInScopeDescendents(child, noInScopeDescendentsCache, yesInScopeDescendentsCache)) {
				yesInScopeDescendentsCache.add(child);
				return true;
			} else {
				noInScopeDescendentsCache.add(child);
			}
		}
		noInScopeDescendentsCache.add(c);
		return false;
	}
	
	@Override
	protected boolean inScope(Component c) {
		return !SnomedUtils.isInternational(c);
	}
	
	private boolean hasTranslation(Concept c) {
		return c.getDescriptions().stream()
				.anyMatch(d -> !d.getLang().equals("en"));
	}

}
