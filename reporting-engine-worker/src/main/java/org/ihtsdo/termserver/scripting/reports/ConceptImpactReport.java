package org.ihtsdo.termserver.scripting.reports;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.HighUsageHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/*
 * RP-1023
 * Loads in a categorised set of ECL queries and compares the results against
 * the previous release to determine any movement of concepts, such as 
 * when modelling has changed in a particular subhierarchy.
 */
public class ConceptImpactReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptImpactReport.class);

	enum DetailLevel { NONE, HIGH_USAGE_MOVED_OUT_ONLY, MOVED_OUT_ONLY, ALL_HIGH_USAGE, ALL }

	private static final String ORGANIZATION_STR = "Organization";
	private static final String EMAIL_LIST = "Email distribution list";
	private static final String GIT_HUB_PROJECT = "content-impact-testing";
	private static final String DUE_TO = " due to: ";

	//List<String> emailList
	private String currentReleaseBranch;
	private String previousReleaseBranch;

	private String organization;
	private OrganizationConfig organizationConfig;
	private List<String> importErrors = new ArrayList<>();

	private HighUsageHelper highUsageHelper;
	private boolean reportHighUsage = false;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = Map.of(ORGANIZATION_STR, "uk-nhs");
		TermServerScript.run(ConceptImpactReport.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_THIRD_PARTY);
		GraphLoader.getGraphLoader().setRecordPreviousState(true);
		super.init(run);
		headers="Category, Name, Description, Production Status, Tags";
		additionalReportColumns="";
		
		organization = run.getParamValue(ORGANIZATION_STR);
		//emailList = Arrays.asList(run.getParamValue(EMAIL_LIST).split(","))
	
		//No point in forming a snapshot in memory if we can't recover the config from GitHub
		//So let's do that up front
		//Load the queries from the GitHub project
		//doGitHubPull()
		try {
			LOGGER.info("Current directory: {}", new File(".").getCanonicalPath());
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
		organizationConfig = loadOrganizationConfig(organization);

		getArchiveManager().setRecordPreviousState(true);

		String prevRelease = project.getMetadata().getPreviousRelease();
		currentReleaseBranch = "MAIN";
		previousReleaseBranch = "MAIN/" + SnomedUtils.formatReleaseDate(prevRelease);

		highUsageHelper = HighUsageHelper.get();
		reportHighUsage = highUsageHelper.containsUsageData(organization);

		summaryTabIdx = PRIMARY_REPORT;
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		List<String> columnHeadings = new ArrayList<>();
		List<String> tabNames = new ArrayList<>();
		generateTabNamesAndColumnHeadings(tabNames, columnHeadings);
		super.postInit(tabNames.toArray(new String[0]), columnHeadings.toArray(new String[0]));

		for (String error : importErrors) {
			report (PRIMARY_REPORT, error);
		}
	}

	private void generateTabNamesAndColumnHeadings(List<String> tabNames, List<String> columnHeadings) {
		//The first couple of tabs for summary and created/inactivated concepts are constant
		tabNames.add("Summary");
		columnHeadings.add("Category, Item, Count");

		tabNames.add("New and Inactivated");
		columnHeadings.add("SCTID, FSN, SemTag, Action");

		//Then we have a tab for each category
		for (EclCategory category : organizationConfig.categories) {
			tabNames.add(category.categoryName);
			columnHeadings.add("ECL Name, Concept, Newly Created, Inactivated, Moved Out, Moved In");
		}
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ORGANIZATION_STR).withType(JobParameter.Type.STRING)
				.add(EMAIL_LIST).withType(JobParameter.Type.STRING)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concept Impact Report")
				.withDescription(
						"This report runs a set of ECL queries taken from the GitHub project concept-movement-testing"
								+ " and compares the results against the previous release to determine any movement of concepts, such as "
								+ "when modelling has changed in a particular subhierarchy. ")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withTag(MS)
				.withParameters(params)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		int reportOutputTab = SECONDARY_REPORT;
		listNewAndInactivatedConcepts(reportOutputTab);

		for (EclCategory category : organizationConfig.categories) {
			reportOutputTab++;
			if (category.queries.isEmpty()) {
				report(reportOutputTab, "No ECL queries configured for this category");
			} else {
				for (EclQueryConfig eclQueryConfig : category.queries) {
					runEcl(eclQueryConfig, reportOutputTab);
				}
			}
		}
	}

	private void listNewAndInactivatedConcepts(int tabIdx) throws TermServerScriptException {
		LOGGER.debug("Sorting all concepts");
		List<Concept> allConceptsSorted = gl.getAllConcepts().stream()
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();
		LOGGER.debug("Sorted");

		for (Concept c : getConceptsNewlyCreated(allConceptsSorted)) {
			incrementSummaryInformation("New Concepts");
			report(tabIdx, c, "Created");
		}

		for (Concept c : getConceptsNewlyInactivated(allConceptsSorted)) {
			incrementSummaryInformation("Inactivated");
			report(tabIdx, c, "Inactivated");
		}
	}

	private List<Concept> getConceptsNewlyCreated(List<Concept> allConceptsSorted) {
		//If it's active, new/changed and doesn't have a previous state, then it's totally new
		return allConceptsSorted.stream()
				.filter(Component::isActiveSafely)
				.filter(Component::isNewOrChangedSinceLastRelease)
				.filter(c -> c.getPreviousState() == null)
				.toList();
	}

	private List<Concept> getConceptsNewlyInactivated(List<Concept> allConceptsSorted) {
		return allConceptsSorted.stream()
				.filter(Component::isNewOrChangedSinceLastRelease)
				.filter(c -> !c.isActiveSafely())
				.toList();
	}

	private void runEcl(EclQueryConfig eclQueryConfig, int tabIdx) throws TermServerScriptException {
		try {
			Collection<Concept> currentConcepts = findConcepts(currentReleaseBranch, eclQueryConfig.ecl);
			Collection<Concept> previousConcepts = findConcepts(previousReleaseBranch, eclQueryConfig.ecl);
			MovementCollections movementCollections = calculateMovmentCollections(currentConcepts, previousConcepts);
			determineHighUsageConcepts(movementCollections);

			//Are we reporting this ECL via summary numbers, or with the details?
			reportMovement(tabIdx, eclQueryConfig, currentConcepts.size(), movementCollections);

		} catch (Exception e) {
			report(tabIdx, eclQueryConfig.name + " ecl attempted " + eclQueryConfig.ecl);
			report(tabIdx, eclQueryConfig.name + " failed due to " + e.getMessage());
		}

	}

	private MovementCollections calculateMovmentCollections(Collection<Concept> currentConcepts, Collection<Concept> previousConcepts) {
		MovementCollections movementCollections = new MovementCollections();
		//Count entirely new concepts as they will not have a previous state
		movementCollections.newConcepts = currentConcepts.stream()
				.filter(c -> !c.hasPreviousStateDataRecorded())
				.toList();

		//Concepts inactivated, are those that were in the previous set, but are now inactive
		movementCollections.inactivatedConcepts = previousConcepts.stream()
				.filter(c -> !c.isActiveSafely())
				.toList();


		//Concepts moved out are those that were in the previous set, not in the current set, but are still active
		movementCollections.conceptsMovedOut = previousConcepts.stream()
				.filter(c -> !currentConcepts.contains(c))
				.filter(Concept::isActiveSafely)
				.toList();

		//Concepts moved in are those that were not in the previous set, but are in the current set, and have a previous state
		movementCollections.conceptsMovedIn = currentConcepts.stream()
				.filter(c -> !previousConcepts.contains(c))
				.filter(Concept::hasPreviousStateDataRecorded)
				.toList();

		return movementCollections;
	}

	private void determineHighUsageConcepts(MovementCollections mc) {
		//Do we have usage data for this organization?
		if (reportHighUsage) {
			//If so, we can determine which of the inactivated concepts are high usage
			mc.highUsageInactivatedConcepts = mc.inactivatedConcepts.stream()
					.filter(c -> highUsageHelper.isHighUsage(c, organization))
					.toList();
			mc.highUsageConceptsMovedOut = mc.conceptsMovedOut.stream()
					.filter(c -> highUsageHelper.isHighUsage(c, organization))
					.toList();
			mc.highUsageConceptsMovedIn = mc.conceptsMovedIn.stream()
					.filter(c -> highUsageHelper.isHighUsage(c, organization))
					.toList();
		}
	}

	private void reportMovement(int tabIdx, EclQueryConfig eclQueryConfig, int currentSize, MovementCollections mc) throws TermServerScriptException {
		//We will always report the summary counts first
		report(tabIdx, eclQueryConfig.name, currentSize, generateSummaryCounts(mc));
	}

	private String[] generateSummaryCounts(MovementCollections mc) {
		return new String[] {
				mc.getNewConceptCount(),
				mc.getInactivatedConceptCount(),
				mc.getConceptsMovedOutCount(),
				mc.getConceptsMovedInCount()
		};
	}

	private OrganizationConfig loadOrganizationConfig(String organization) throws TermServerScriptException {
		//Recursively search the local folder and create a
		//ECLCategory for each subfolder found
		//and then an ECLQueryConfig for each file found
		//in the subfolder
		OrganizationConfig config = new OrganizationConfig();
		config.organization = organization;
		config.categories = new ArrayList<>();
		Path rootPath = Paths.get(GIT_HUB_PROJECT, organization);
		ObjectMapper objectMapper = new ObjectMapper();

		LOGGER.info("Loading organization ECL config from {}", rootPath);
		try (Stream<Path> paths = Files.walk(rootPath)) {
			paths.filter(Files::isDirectory)
				.forEach(path -> {
					//skip the top level directory
					if (!path.equals(rootPath)) {
						EclCategory category = new EclCategory();
						category.categoryName = path.getFileName().toString();
						category.queries = new ArrayList<>();


						try (Stream<Path> files = Files.list(path)) {
							files.filter(Files::isRegularFile)
									.forEach(file -> {
										EclQueryConfig queryConfig = null;
										try {
											queryConfig = objectMapper.readValue(file.toFile(), EclQueryConfig.class);
											queryConfig.name = file.getFileName().toString();
											category.queries.add(queryConfig);
											config.incrementEclCount();
										} catch (IOException e) {
											importErrors.add("Failed to read query config from " + file + DUE_TO + e.getMessage());
										}
									});
						} catch (IOException e) {
							importErrors.add("Failed to import category " + category + DUE_TO + e.getMessage());
						}
						config.categories.add(category);
					}
			});
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to load '" + organization + "' organization config from " + rootPath, e);
		}

		LOGGER.info("Loaded {} ECL configs from {}", config.eclCount, rootPath);
		return config;
	}

	class MovementCollections {
		Collection<Concept> newConcepts;
		Collection<Concept> inactivatedConcepts;
		Collection<Concept> conceptsMovedOut;
		Collection<Concept> conceptsMovedIn;

		Collection<Concept> highUsageInactivatedConcepts;
		Collection<Concept> highUsageConceptsMovedOut;
		Collection<Concept> highUsageConceptsMovedIn;

		public String getNewConceptCount() {
			return formatCount(newConcepts, null);
		}

		public String getInactivatedConceptCount() {
			return formatCount(inactivatedConcepts, highUsageInactivatedConcepts);
		}

		public String getConceptsMovedOutCount() {
			return formatCount(conceptsMovedOut, highUsageConceptsMovedOut);
		}

		public String getConceptsMovedInCount() {
			return formatCount(conceptsMovedIn, highUsageConceptsMovedIn);
		}

		private String formatCount(Collection<Concept> concepts, Collection<Concept> highUsage) {
			StringBuilder sb = new StringBuilder();
			sb.append(concepts.size());
			if (reportHighUsage && highUsage != null) {
				sb.append(" (");
				sb.append(highUsage.size());
				sb.append(" high usage)");
			}
			return sb.toString();
		}
	}
	
	static class OrganizationConfig {
		String organization;
		List<EclCategory> categories;
		int eclCount = 0;

		public void incrementEclCount() {
			eclCount++;
		}
	}

	static class EclCategory {
		String categoryName;
		List<EclQueryConfig> queries;
	}

	static class EclQueryConfig {
		@JsonProperty("name")
		String name;

		@JsonProperty("ecl")
		String ecl;
		ImpactAlertLevels alertLevels;
		DetailLevel detailLevel;
	}

	static class ImpactAlertLevels {
		AlertTriggerEffect alertNew;
		AlertTriggerEffect alertInactive;
		AlertTriggerEffect highUsageAlertInactive;
		AlertTriggerEffect alertMovedOut;
		AlertTriggerEffect alertMovedIn;
		AlertTriggerEffect highUsageAlertMovedOut;
		AlertTriggerEffect highUsageAlertMovedIn;

	}

	static class AlertTriggerEffect {
		int triggerCount;
		int setLevel;
	}

}
