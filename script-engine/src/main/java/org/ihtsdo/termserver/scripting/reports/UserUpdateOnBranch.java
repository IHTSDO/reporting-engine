package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.traceability.domain.*;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class UserUpdateOnBranch extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserUpdateOnBranch.class);

	public static final String USER_LIST = "Users";
	public static final String PROJECT_LIST = "Projects";
	public static final String FROM_ET = "From Effective Time";
	public static final DecimalFormat DP = new DecimalFormat("#.00");

	private TraceabilityServiceClient client;
	private String users;
	private String projects;
	private String fromEffectiveTime;
	private Map<String, List<Concept>> userConcepts = new HashMap<>();
	private Set<String> commitsReported = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(USER_LIST, "wscampbell,ssantamaria,pwilliams");
		params.put(PROJECT_LIST, "MAIN/SNOMEDCT-CSR/NEBCSR,MAIN/CSRINT1");
		params.put(FROM_ET, "2021-01-01");
		TermServerScript.run(UserUpdateOnBranch.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		users = run.getParamValue(USER_LIST);
		projects = run.getParamValue(PROJECT_LIST);
		fromEffectiveTime = run.getParamValue(FROM_ET);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Summary",
				"Detail"
		};
		String[] columnHeadings = new String[] {
				"User, Task, SCTID, FSN, Axiom ET",
				"User, Branch, Commit, Action(s), Promoted Date, Highest Promoted Branch, ",
		};
		postInit(GFOLDER_ADHOC_REPORTS, tabNames, columnHeadings, false);
		client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(USER_LIST).withType(JobParameter.Type.STRING).withMandatory()
				.add(PROJECT_LIST).withType(JobParameter.Type.STRING).withMandatory()
				.add(FROM_ET).withType(JobParameter.Type.STRING).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("User update on Branch")
				.withDescription("This report lists concepts that were modelled (or had model updated) by one of the specified users, one on of the specified branches.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<AxiomEntry> allAxiomsModifiedInTimePeriod = getAllAxiomsModifiedInTimePeriod();
		Map<String, List<Activity>> axiomActivityMap = getTraceabilityInfoForSpecifiedUsers();
		LOGGER.info("Checking activity for relevance on {} concepts", allAxiomsModifiedInTimePeriod.size());
		int conceptCount = 0;
		for (AxiomEntry a : allAxiomsModifiedInTimePeriod) {
			//Was this an axiom that also returned traceability for the specified users on those specific branches?
			if (axiomActivityMap.containsKey(a.getId())) {
				for (Activity activity : axiomActivityMap.get(a.getId())) {
					Concept c = gl.getConceptSafely(a.getReferencedComponentId());
					if (!alreadySeenForUser(activity.getUsername(), c)) {
						report(PRIMARY_REPORT, activity.getUsername(), getLastItem(activity.getBranch()), c.getId(), c.getFsn(), a.getEffectiveTime());
					}
					if (!alreadySeenCommitForUserOnBranch(activity)) {
						report(SECONDARY_REPORT, activity.getUsername(), activity.getBranch(), activity.getCommitDate(), getConceptsWithActions(activity), activity.getPromotionDate(), activity.getHighestPromotedBranch());
					}
				}
			}

			if (++conceptCount % 100 == 0) {
				String perc = DP.format((conceptCount * 100) / (float)allAxiomsModifiedInTimePeriod.size());
				LOGGER.info("Processed {} concepts = {}%", conceptCount, perc);
			}

		}
		LOGGER.info ("Job complete");
	}

	private boolean alreadySeenCommitForUserOnBranch(Activity activity) {
		String key = activity.getUsername() + activity.getBranch() + activity.getCommitDate();
		if (commitsReported.contains(key)) {
			return true;
		} else {
			commitsReported.add(key);
			return false;
		}
	}

	private boolean alreadySeenForUser(String username, Concept c) {
		//Have we already reported on this concept for this user?
		List<Concept> concepts = userConcepts.computeIfAbsent(username, k -> new ArrayList<>());
		if (concepts.contains(c)) {
			return true;
		}
		concepts.add(c);
		return false;
	}

	public static String getLastItem(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		String[] parts = input.split("/");
		return parts[parts.length - 1];
	}

	private String getConceptsWithActions(Activity activity) {
		return activity.getConceptChanges().stream()
				.map(change -> toString(change))
				.collect(Collectors.joining("\n"));
	}

	private String toString(ConceptChange change) {
		Concept c = gl.getConceptSafely(change.getConceptId());
		ComponentChange firstChange = change.getComponentChanges().iterator().next();
		return firstChange.getChangeType() + " " + c + " -> axiom " + firstChange.getComponentId();
	}

	private Map<String, List<Activity>> getTraceabilityInfoForSpecifiedUsers() throws TermServerScriptException {
		try {
			Map<String, List<Activity>> axiomActivityMap = new HashMap<>();
			List<Activity> activities = client.getActivitiesForUsersOnBranches("733073007", users, projects, fromEffectiveTime);
			for (Activity activity : activities) {
				for (ConceptChange conceptChange : activity.getConceptChanges()) {
					for (ComponentChange componentChange : conceptChange.getComponentChanges()) {
						axiomActivityMap.computeIfAbsent(componentChange.getComponentId(), k -> new ArrayList<>()).add(activity);
					}
				}
			}
			return axiomActivityMap;
		} catch (InterruptedException e) {
			throw new TermServerScriptException("Failed to get traceability info for specified users", e);
		}
	}

	private List<AxiomEntry> getAllAxiomsModifiedInTimePeriod() {
		List<AxiomEntry> axiomsModifiedInTimePeriod = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			AxiomEntry a = axiomModifiedInTimePeriod(c);
			if (a != null) {
				axiomsModifiedInTimePeriod.add(a);
			}
		}
		return axiomsModifiedInTimePeriod;
	}

	private AxiomEntry axiomModifiedInTimePeriod(Concept c) {
		AxiomEntry axiomModifiedInTimePeriod = null;
		for (AxiomEntry a : c.getAxiomEntries()) {
			if (a.getEffectiveTime().compareTo(fromEffectiveTime) > 0 &&
					(axiomModifiedInTimePeriod == null ||
					a.getEffectiveTime().compareTo(axiomModifiedInTimePeriod.getEffectiveTime()) > 0)) {
				axiomModifiedInTimePeriod = a;
			}
		}
		return axiomModifiedInTimePeriod;
	}

}
