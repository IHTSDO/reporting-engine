package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompareConceptsBetweenReleases extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(CompareConceptsBetweenReleases.class);

	private static final String CONCEPT_IDS = "Concepts";
	private static final String PREV_RELEASE = "Previous Release";
	private static final String THIS_RELEASE = "This Release";

	protected String prevRelease;
	protected String projectKey;

	private Set<String> conceptIdsOfInterest = new HashSet<>();
	private Map <String, ConceptState> conceptStates = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(CONCEPT_IDS, "22298006,386661006,713427006,44054006,444814009,84757009,195967001,128462008,38341003,233604007");
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20210731T120000Z.zip");
		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20240901T120000Z.zip");
		TermServerScript.run(CompareConceptsBetweenReleases.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(CONCEPT_IDS).withType(JobParameter.Type.CONCEPT_LIST).withMandatory()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withMandatory()
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Compare Concepts Between Releases")
				.withDescription("This report compares properties of specified concepts between two releases.  The issue count shows the number of differences in those concepts between the two specified releases")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);

		if (!StringUtils.isEmpty(run.getParamValue(CONCEPT_IDS))) {
			getConceptsOfInterest(run.getMandatoryParamValue(CONCEPT_IDS));
		}
		super.init(run);
	}

	private void getConceptsOfInterest(String sctIds) {
		// Regular expression to match numbers before the pipe symbol
		String regex = "\\d+(?= \\|)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(sctIds);

		// Extract all numbers matching the regex
		while (matcher.find()) {
			conceptIdsOfInterest.add(matcher.group());
		}
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		projectKey = getProject().getKey();
		LOGGER.info("Historic data being imported, wiping Graph Loader for safety.");
		getArchiveManager().reset();

		if (!StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) &&
				StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			throw new TermServerScriptException("This release must be specified if previous release is.");
		}

		if (!StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			projectKey = getJobRun().getParamValue(THIS_RELEASE);
			//Have we got what looks like a zip file but someone left the .zip off?
			if (projectKey.contains("T120000") && !projectKey.endsWith(".zip")) {
				throw new TermServerScriptException("Suspect release '" + projectKey + "' should end with .zip");
			}
			//If this release has been specified, the previous must also be, explicitly
			if (StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE))) {
				throw new TermServerScriptException("Previous release must be specified if current release is.");
			}
		}

		prevRelease = getJobRun().getParamValue(PREV_RELEASE);
		if (StringUtils.isEmpty(prevRelease)) {
			prevRelease = getProject().getMetadata().getPreviousPackage();
		}

		getProject().setKey(prevRelease);
		//If we have a task defined, we need to shift that out of the way while we're loading the previous package
		String task = getJobRun().getTask();
		getJobRun().setTask(null);
		try {
			ArchiveManager mgr = getArchiveManager();
			mgr.setLoadEditionArchive(true);
			mgr.loadSnapshot(fsnOnly);
			populateConceptState();
			mgr.reset();
			getJobRun().setTask(task);
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Historic Data Generation failed due to " + e.getMessage(), e);
		}
		loadCurrentPosition();
	}

	private void populateConceptState() throws TermServerScriptException {
		for (String sctid : conceptIdsOfInterest) {
			Concept c = gl.getConcept(sctid, false, false);
			conceptStates.put(sctid, new ConceptState(c));
		}
		LOGGER.info("Populated 'previous' state of {} concepts", conceptStates.size());
	}

	protected void loadCurrentPosition() throws TermServerScriptException {
		LOGGER.info("Previous Data Generated, now loading 'current' position");
		ArchiveManager mgr = getArchiveManager();
		//We cannot just add in the project delta because it might be that - for an extension
		//the international edition has also been updated.   So recreate the whole snapshot
		mgr.setLoadEditionArchive(false);
		getProject().setKey(projectKey);
		mgr.loadSnapshot(false);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, Active, FSN, DefintionStatus, Descriptions, StatedExpression, InferredExpression",
				"Id, Active, FSN, DefintionStatus, Descriptions, StatedExpression, InferredExpression",
				"Id, FSN, SemTag, Field, Before, After",
		};
		String[] tabNames = new String[] {
				getDateFromRelease(prevRelease),
				getDateFromRelease(projectKey),
				"Differences"
		};
		super.postInit(GFOLDER_QI, tabNames, columnHeadings, false);
	}

	private String getDateFromRelease(String release) {
		String regex = "\\d{8}(?=T)";

		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(release);

		if (matcher.find()) {
			return matcher.group();
		}
		return release;
	}


	@Override
	public void runJob() throws TermServerScriptException {
		examineConcepts();
		LOGGER.info("Job complete");
	}
	
	public void examineConcepts() throws TermServerScriptException { 
		LOGGER.info("Examining {} concepts of interest", conceptIdsOfInterest.size());
		for (String sctid : conceptIdsOfInterest) {
			Concept c = gl.getConcept(sctid, false, false);
			ConceptState previousState = conceptStates.get(c.getConceptId());
			report(PRIMARY_REPORT, previousState.toReportString());
			report(SECONDARY_REPORT, new ConceptState(c).toReportString());
			for (Difference difference : previousState.findDifferences(c)) {
				countIssue(c);
				report(TERTIARY_REPORT, c, difference.fieldName, difference.before, difference.after);
			}
		}
	}

	private class ConceptState {
		String id;
		boolean exists;
		boolean active;
		String fsn;
		DefinitionStatus definitionStatus;
		String descriptions;
		String statedExpression;
		String inferredExpression;

		String[] toReportString() {
			return new String[] {id, active?"Y":"N", fsn, definitionStatus.toString(), descriptions, statedExpression, inferredExpression};
		}

		ConceptState(Concept c) {
			if (c == null) {
				exists = false;
			} else {
				exists = true;
				id = c.getConceptId();
				active = c.isActive();
				fsn = c.getFsn();
				definitionStatus = c.getDefinitionStatus();
				descriptions = SnomedUtils.getDescriptions(c);
				statedExpression = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				inferredExpression = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
			}
		}

		List<Difference> findDifferences(Concept c) {
			List<Difference> differences = new ArrayList<>();
			if (!exists && c != null) {
				Difference d = new Difference();
				d.fieldName = "Exists";
				d.before = "N";
				d.after = "Y";
				differences.add(d);
				return differences;
			} else if (c == null) {
				Difference d = new Difference();
				d.fieldName = "Exists";
				d.before = "Y";
				d.after = "N";
				differences.add(d);
				return differences;
			}

			if (!active == c.isActiveSafely()) {
				Difference d = new Difference();
				d.fieldName = "Active";
				d.before = active?"Y":"N";
				d.after = c.isActiveSafely()? "Y":"N";
				differences.add(d);
			}

			if (!fsn.equals(c.getFsn())) {
				Difference d = new Difference();
				d.fieldName = "FSN";
				d.before = fsn;
				d.after = c.getFsn();
				differences.add(d);
			}

			if (!definitionStatus.equals(c.getDefinitionStatus())) {
				Difference d = new Difference();
				d.fieldName = "Definition Status";
				d.before = definitionStatus.toString();
				d.after = c.getDefinitionStatus().toString();
				differences.add(d);
			}

			if (!descriptions.equals(SnomedUtils.getDescriptions(c))) {
				Difference d = new Difference();
				d.fieldName = "Descriptions";
				d.before = descriptions;
				d.after = SnomedUtils.getDescriptions(c);
				differences.add(d);
			}

			if (!statedExpression.equals(c.toExpression(CharacteristicType.STATED_RELATIONSHIP))) {
				Difference d = new Difference();
				d.fieldName = "Stated Expression";
				d.before = statedExpression;
				d.after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				differences.add(d);
			}

			if (!inferredExpression.equals(c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP))) {
				Difference d = new Difference();
				d.fieldName = "Inferred Expression";
				d.before = inferredExpression;
				d.after = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				differences.add(d);
			}
			return differences;
		}
	}

	private class Difference {
		String fieldName;
		String before;
		String after;
	}
}
