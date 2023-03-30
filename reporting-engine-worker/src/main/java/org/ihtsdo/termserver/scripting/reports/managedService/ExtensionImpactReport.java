package org.ihtsdo.termserver.scripting.reports.managedService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
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
public class ExtensionImpactReport extends HistoricDataUser implements ReportClass {
	
	private static String INTERNATIONAL_RELEASE = "Proposed International Release Archive";
	
	private String incomingDataKey;
	private Map<String, Datum> incomingData;
	
	private Map<Concept, Concept> usedInStatedModellingMap; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(INTERNATIONAL_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20220131T120000Z.zip");
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
				.build();
	}

	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		
		origProject = run.getProject();
		if (!StringUtils.isEmpty(run.getParamValue(INTERNATIONAL_RELEASE))) {
			projectName = run.getParamValue(INTERNATIONAL_RELEASE);
			run.setProject(projectName);
		}
		
		summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
		
		if (!getProject().getBranchPath().startsWith("MAIN/SNOMEDCT-")) {
			throw new TermServerScriptException("Cannot work with '" + run.getProject() + "'. This report can only be run against an extension. ");
		}
		
		if (!StringUtils.isEmpty(getJobRun().getTask())) {
			throw new TermServerScriptException("This report cannot be run against tasks");
		}
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		boolean compareTwoSnapshots = false; 
		info("International Release data being imported, wiping Graph Loader for safety.");
		getArchiveManager(true).reset(false);
		Project previousProject = project.clone();
		boolean loadEditionArchive = false;
		
		if (StringUtils.isEmpty(getJobRun().getParamValue(INTERNATIONAL_RELEASE))) {
			Branch branch = tsClient.getBranch("MAIN");
			project.setBranchPath("MAIN");
			project.setKey("MAIN");
			project.setMetadata(branch.getMetadata());
		} else {
			projectKey = getJobRun().getParamValue(INTERNATIONAL_RELEASE);
			project.setKey(projectKey);
			project.setBranchPath(projectKey);
			loadEditionArchive = true;
		}

		try {
			incomingDataKey = project.getKey();
			ArchiveManager mgr = getArchiveManager(true);
			mgr.setLoadEditionArchive(loadEditionArchive);
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
		info("Setting dependency archive: " + thisDependency);
		setDependencyArchive(thisDependency);
		getArchiveManager().setPopulatePreviousTransativeClosure(false);
		super.loadCurrentPosition(false, fsnOnly);
	}

	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}
		
		String[] columnHeadings = new String[] {"Summary Item, Count",
												"SCTID, FSN, SemTag, InactivatedWithInferredExtensionChildren, example, inactivatedUsedInStatedModelling, example",
												"SCTID, FSN, SemTag, InactivatedWithInferredExtensionChildren, example, inactivatedUsedInStatedModelling, example",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag, Defn Status Change",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag, Text Definition",
												"SCTID, FSN, SemTag"};
		String[] tabNames = new String[] {	"Summary Counts",  //PRIMARY
											"Inactive",    //SECONDARY
											"New Concepts",    //TERTIARY
											"Modeling",        //QUAD
											"Translation",     //
											"DefnStatus",
											"New FSNs",
											"Text Defn",
											"ICD-O"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void runJob() throws TermServerScriptException {
		//We've always loaded historic data as 'prev' but in this case, we're looking the release that
		//we're about to upgrade to.  So we'll give that a more appropriate name
		info ("Loading Previous Data");
		incomingData = loadData(incomingDataKey);
		
		info("Populating map of all concepts used in stated modelling");
		populateStatedModellingMap();
		
		Executor executor = Executors.newFixedThreadPool(7);
		
		//Work through the top level hierarchies
		List<Concept> topLevelHierarchies = SnomedUtils.sort(ROOT_CONCEPT.getDescendents(IMMEDIATE_CHILD));
		for (Concept topLevelConcept : topLevelHierarchies) {
			info("Processing - " + topLevelConcept);
			Set<String> thisHierarchy = getHierarchy(topLevelConcept);
			reportInactivations(topLevelConcept, thisHierarchy);
		}
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


	private void reportInactivations(Concept topLevelConcept, Set<String> thisHierarchy) throws TermServerScriptException {
		info("Reporting Inactivations");
		int inactivatedWithInferredExtensionChildren = 0;
		int inactivatedUsedInStatedModelling = 0;
		String[] examples = new String[2];
		
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
			
			//Does this concept have any inScope children?  We'll step this with new code
			//to short cut finding the complete set and allocating memory for that
			if (hasInScopeDescendents(currentConcept, noInScopeDescendentsCache, yesInScopeDescendentsCache)) {
				inactivatedWithInferredExtensionChildren++;
				examples[0] = currentConcept.toString();
			}
			
			Concept usedIn = usedInStatedModellingMap.get(currentConcept);
			if (usedIn != null) {
				inactivatedUsedInStatedModelling++;
				examples[1] = currentConcept + " used in :\n" + usedIn.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			}
		}
		report(SECONDARY_REPORT, topLevelConcept, inactivatedWithInferredExtensionChildren, examples[0], inactivatedUsedInStatedModelling, examples[1]);
	}

	private void populateStatedModellingMap() {
		usedInStatedModellingMap = new HashMap<>();
		//I'm not calling getRelationships(Active, Stated) because that causes memory to be allocated
		//and we're going through A LOT of loops here, so just test values
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive() && inScope(c)) {
				for (Relationship r : c.getRelationships()) {
					if (r.isActive() 
							&& r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP)
							&& !r.getType().equals(IS_A)) {
						if (!usedInStatedModellingMap.containsKey(r.getType())) {
							usedInStatedModellingMap.put(r.getType(), c);
						}
						
						if (r.isNotConcrete() && !usedInStatedModellingMap.containsKey(r.getTarget())) {
							usedInStatedModellingMap.put(r.getTarget(), c);
						}
					}
				}
			}
		}
	}

	private boolean hasInScopeDescendents(Concept c, Set<Concept> noInScopeDescendentsCache, Set<Concept> yesInScopeDescendentsCache) {
		for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//If we already know that this concept has no descendents, then no need to check again
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
}
